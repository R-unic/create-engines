package com.createengines.content.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.CETriggers;
import com.createengines.advancement.Milestone;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.engine.topology.FiringPlanner.Plan;
import com.createengines.engine.topology.FiringPlanner.Style;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Sets an engine's firing order for the player. Using it on any block of a built engine works out the best spacing of
 * the firings that the engine's layout allows, then sets every crank throw and every cam lobe to match (see
 * {@link FiringPlanner}). Sneak-using it, in the air or on a block, changes what it is allowed to do:
 * <ul>
 *     <li><b>Smooth</b> sets the throws to any angle: the most even firing the layout allows.</li>
 *     <li><b>Cross-plane</b> keeps the throws a quarter turn apart, as in a V8.</li>
 *     <li><b>Flat-plane</b> keeps the throws a half turn apart, as in a flat-plane four or a boxer.</li>
 *     <li><b>Cams only</b> leaves the crank exactly as built and times the lobes to it.</li>
 * </ul>
 */
public class TimingToolItem extends Item {

	private static final String STYLE_TAG = "Style";
	private static final Style[] STYLES = Style.values();

	public TimingToolItem(Properties properties) {
		super(properties);
	}

	public static Style style(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return Style.SMOOTH;
		int index = data.copyTag()
			.getInt(STYLE_TAG);
		return STYLES[Math.floorMod(index, STYLES.length)];
	}

	private static void setStyle(ItemStack stack, Style style) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(STYLE_TAG, style.ordinal()));
	}

	private static Component styleName(Style style) {
		return CELang.translate("tool.timing_tool.style." + style.name()
			.toLowerCase(java.util.Locale.ROOT))
			.component();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown())
			return InteractionResultHolder.pass(stack);
		cycle(stack, level, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		ItemStack stack = context.getItemInHand();
		if (player.isShiftKeyDown()) {
			cycle(stack, level, player);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!level.isClientSide)
			time(level, context.getClickedPos(), stack, player);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void cycle(ItemStack stack, Level level, Player player) {
		Style next = STYLES[(style(stack).ordinal() + 1) % STYLES.length];
		if (level.isClientSide)
			return;
		setStyle(stack, next);
		player.displayClientMessage(CELang.translate("tool.timing_tool.style", styleName(next))
			.component(), true);
		level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(CELang.translate("tool.timing_tool.style", styleName(style(stack)))
			.component()
			.copy()
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(CELang.translate("tool.timing_tool.hint")
			.component()
			.copy()
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	// Working out and applying a plan

	/** The cylinders being timed, and where in the world each throw and lobe is. */
	private record Job(List<FiringPlanner.Cylinder> cylinders, List<BlockPos> throwPositions,
					   List<CrankshaftBlockEntity> throwEntities, List<Integer> throwSenses, List<BlockPos> lobePositions,
					   List<CamshaftBlockEntity> lobeEntities, int skipped) {
	}

	/**
	 * What timing an engine did: the plan it set, and how many cylinders were left out of it. The plan is null if the
	 * engine had no working cylinder with a cam lobe to time. {@code lines} is how many crankshafts it was timed across.
	 */
	public record Result(@Nullable Plan plan, int skipped, int cylinders, int lines) {
	}

	private static void time(Level level, BlockPos clicked, ItemStack stack, Player player) {
		Result result = apply(level, clicked, style(stack));
		if (result == null) {
			tell(player, CELang.translate("tool.timing_tool.no_engine")
				.component(), ChatFormatting.RED);
			return;
		}
		if (result.plan() == null) {
			tell(player, CELang.translate("tool.timing_tool.no_cylinders")
				.component(), ChatFormatting.RED);
			return;
		}
		report(player, result.plan(), result.skipped());
		if (result.lines() > 1)
			player.displayClientMessage(CELang.translate("tool.timing_tool.coupled", result.lines())
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
		level.playSound(null, clicked, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 1.8f);
		Plan plan = result.plan();
		if (plan.even() && plan.order().length >= 4 && plan.mistimed() == 0 && player instanceof ServerPlayer server)
			CETriggers.EVENT.get()
				.trigger(server, Milestone.TOOL_TIMED);
	}

	/**
	 * Times the engine that the block at {@code clicked} belongs to: works out the plan for {@code style} and sets every
	 * throw and lobe to it. Returns null if the block is not part of an engine.
	 */
	public static @Nullable Result apply(Level level, BlockPos clicked, Style style) {
		Set<BlockPos> seeds = EngineScanner.crankSeeds(level, clicked);
		EngineLayout layout = seeds.isEmpty() ? null : EngineScanner.scan(level, seeds.iterator()
			.next());
		if (layout == null)
			return null;
		Job job = plan(level, layout);
		if (job.cylinders()
			.isEmpty())
			return new Result(null, job.skipped(), 0, layout.lines());

		int direction = direction(level, layout);
		double[] throwAngles = new double[job.throwEntities()
			.size()];
		for (int i = 0; i < throwAngles.length; i++)
			throwAngles[i] = job.throwEntities()
				.get(i)
				.throwAngle();
		for (int i = 0; i < throwAngles.length; i++)
			throwAngles[i] = wrap(job.throwSenses()
				.get(i) * throwAngles[i]);
		double[] lobeAngles = new double[job.lobeEntities()
			.size()];
		for (int i = 0; i < lobeAngles.length; i++)
			lobeAngles[i] = job.lobeEntities()
				.get(i)
				.lobeAngle();
		Plan plan = FiringPlanner.solve(style, direction, throwAngles, lobeAngles, job.cylinders());

		if (style != Style.CAMS_ONLY)
			for (int i = 0; i < throwAngles.length; i++)
				job.throwEntities()
					.get(i).throwAngle.setValue(throwStep(job.throwSenses()
					.get(i) * plan.throwAngles()[i]));
		for (int i = 0; i < lobeAngles.length; i++)
			job.lobeEntities()
				.get(i).lobeAngle.setValue((int) Math.round(plan.lobeAngles()[i] / FiringPlanner.LOBE_STEP));

		return new Result(plan, job.skipped(), job.cylinders()
			.size(), layout.lines());
	}

	private static double wrap(double angle) {
		return (angle % 360 + 360) % 360;
	}

	/** The value a throw's scroll box takes for {@code angle} degrees. */
	private static int throwStep(double angle) {
		return Math.floorMod((int) Math.round(angle / FiringPlanner.THROW_STEP), (int) (360 / FiringPlanner.THROW_STEP));
	}

	/** Which way the crank turns, +1 if it is stopped. */
	private static int direction(Level level, EngineLayout layout) {
		if (level.getBlockEntity(layout.primary()) instanceof CrankshaftBlockEntity crank)
			return crank.getTheoreticalSpeed() < 0 ? -1 : 1;
		return 1;
	}

	/** Collects the working cylinders that have a cam lobe to be timed by, with the throws and lobes they use. */
	private static Job plan(Level level, EngineLayout layout) {
		Map<BlockPos, Integer> throwIndex = new LinkedHashMap<>();
		Map<BlockPos, Integer> lobeIndex = new LinkedHashMap<>();
		List<FiringPlanner.Cylinder> cylinders = new ArrayList<>();
		List<CrankshaftBlockEntity> throwEntities = new ArrayList<>();
		List<Integer> throwSenses = new ArrayList<>();
		List<CamshaftBlockEntity> lobeEntities = new ArrayList<>();
		int skipped = 0;
		for (EngineLayout.Cylinder cylinder : layout.cylinders()) {
			BlockPos lobe = cylinder.lobe();
			if (CylinderFault.disables(cylinder.faults()) || lobe == null
				|| !(level.getBlockEntity(cylinder.throwPos()) instanceof CrankshaftBlockEntity crank)
				|| crank.throwAngle == null || !(level.getBlockEntity(lobe) instanceof CamshaftBlockEntity cam)
				|| cam.lobeAngle == null) {
				skipped++;
				continue;
			}
			int throwNumber = throwIndex.computeIfAbsent(cylinder.throwPos(), $ -> {
				throwEntities.add(crank);
					throwSenses.add(cylinder.sense());
				return throwEntities.size() - 1;
			});
			int lobeNumber = lobeIndex.computeIfAbsent(lobe, $ -> {
				lobeEntities.add(cam);
				return lobeEntities.size() - 1;
			});
			// Top dead centre is the bore's own offset less the throw angle, so adding the throw back gives the offset. A
			// crank line that gearing turns the other way is planned as its mirror image: both angles flip sign
			double bore = Math.round(((cylinder.tdcAngle() + cylinder.sense() * crank.throwAngle()) % 360 + 360) % 360 * 100) / 100d;
			cylinders.add(new FiringPlanner.Cylinder(throwNumber, bore, lobeNumber));
		}
		return new Job(cylinders, List.copyOf(throwIndex.keySet()), throwEntities, throwSenses, List.copyOf(lobeIndex.keySet()),
			lobeEntities, skipped);
	}

	private static void report(Player player, Plan plan, int skipped) {
		int count = plan.order().length;
		StringBuilder order = new StringBuilder();
		for (int index : plan.order()) {
			if (!order.isEmpty())
				order.append('-');
			order.append(index + 1);
		}
		Component message;
		ChatFormatting colour = ChatFormatting.GREEN;
		if (count < 2)
			message = CELang.translate("tool.timing_tool.single")
				.component();
		else if (plan.even())
			message = CELang.translate("tool.timing_tool.even", count, Math.round(720f / count), order.toString())
				.component();
		else {
			colour = ChatFormatting.GOLD;
			message = CELang.translate("tool.timing_tool.uneven", count, gaps(plan), order.toString())
				.component();
		}
		tell(player, message, colour);
		if (plan.mistimed() > 0)
			player.displayClientMessage(CELang.translate("tool.timing_tool.shared", plan.mistimed())
				.component()
				.copy()
				.withStyle(ChatFormatting.GOLD), false);
		if (skipped > 0)
			player.displayClientMessage(CELang.translate("tool.timing_tool.skipped", skipped)
				.component()
				.copy()
				.withStyle(ChatFormatting.GRAY), false);
	}

	/** The gaps between firings, in degrees, in firing order, for the message. */
	private static String gaps(Plan plan) {
		StringBuilder text = new StringBuilder();
		double[] intervals = FiringPlanner.intervals(plan.fireAngles());
		java.util.Arrays.sort(intervals);
		double last = Double.NaN;
		for (double gap : intervals) {
			if (Math.abs(gap - last) < 0.5)
				continue;
			last = gap;
			if (!text.isEmpty())
				text.append(" / ");
			text.append(Math.round(gap))
				.append('°');
		}
		return text.toString();
	}

	private static void tell(Player player, Component message, ChatFormatting colour) {
		player.displayClientMessage(message.copy()
			.withStyle(colour), true);
	}
}

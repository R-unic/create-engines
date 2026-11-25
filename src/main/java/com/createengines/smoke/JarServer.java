package com.createengines.smoke;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Verification track item 5, the server half: boots a real NeoForge dedicated server with the built jar and Create and
 * nothing else, the way a server owner would. The development runs load the classes folder, which holds the test harnesses
 * and hides a class the jar leaves out or a resource it does not carry.
 *
 * <p>Two boots of one new world. The first places every block the jar has a blockstate for and builds the fixture engines
 * from console commands, lets them tick, and stops. The second loads that world again and expects the same blocks and the
 * same engines turning. Both logs go through the smoke log scanner.
 *
 * <p>The server install is made once with the NeoForge installer and is not downloaded here. Only its libraries and its
 * accepted {@code eula.txt} are used: the run happens in a folder of its own, so the install's world and mods are never
 * touched. This build never accepts the EULA itself.
 *
 * <p>Arguments: {@code --install DIR --jar MOD_JAR --create CREATE_VERSION --work DIR --fixtures DIR [--allow FILE]}.
 * Pure Java with no Minecraft imports.
 */
public final class JarServer {

	/** Lines a server prints in answer to a command end here; {@code say} runs after every command sent before it. */
	private static final String MARK = "ce-mark-";
	private static final int GROUND = -60;
	private static final int STOP_SECONDS = 120;
	private static final int PER_ROW = 10;
	private static final int SPACING = 8;
	private static final Pattern NUMBER = Pattern.compile("has the following block data: (-?[0-9.E]+)f?");

	private final SmokeReport report = new SmokeReport("jar server");

	private JarServer() {
	}

	public static void main(String[] args) throws Exception {
		Map<String, String> options = new LinkedHashMap<>();
		for (int i = 0; i + 1 < args.length; i += 2)
			options.put(args[i], args[i + 1]);
		for (String needed : List.of("--install", "--jar", "--create", "--work", "--fixtures"))
			if (!options.containsKey(needed))
				throw new IllegalArgumentException("missing " + needed);
		JarServer run = new JarServer();
		try {
			run.run(options);
		} catch (IOException | RuntimeException e) {
			run.report.fail("server", "the run stopped", String.valueOf(e.getMessage()));
		}
		run.report.markCompleted();
		System.out.println(run.report.render());
		run.report.write(Path.of(options.get("--work"), "report"));
		if (!run.report.passed())
			System.exit(1);
	}

	/** One block to place, and where. */
	record Placed(String id, int x, int y, int z) {
		String at() {
			return x + " " + y + " " + z;
		}
	}

	private void run(Map<String, String> options) throws IOException, InterruptedException {
		Path install = Path.of(options.get("--install"));
		Path jar = Path.of(options.get("--jar"));
		Path work = Path.of(options.get("--work"));
		Path server = work.resolve("server");
		String neo = prepare(install, jar, options.get("--create"), server);
		report.pass("server", "NeoForge " + neo + ", Create " + options.get("--create") + ", " + jar.getFileName());

		List<Placed> blocks = grid(blockIds(jar));
		if (blocks.isEmpty())
			throw new IllegalStateException("the jar has no blockstates");
		List<List<String>> engines = new ArrayList<>();
		List<String> engineNames = new ArrayList<>();
		List<Placed> cranks = new ArrayList<>();
		try (Stream<Path> files = Files.list(Path.of(options.get("--fixtures")))) {
			for (Path fixture : files.filter(p -> p.getFileName().toString().matches("engine_v2_0_0_\\w+\\.snbt")).sorted().toList()) {
				int originZ = 80 + engines.size() * 32;
				List<Placed> found = new ArrayList<>();
				engines.add(fixtureCommands(Files.readString(fixture, StandardCharsets.UTF_8), 0, GROUND, originZ, found));
				engineNames.add(fixture.getFileName().toString());
				cranks.add(found.isEmpty() ? null : found.get(0));
			}
		}
		if (engines.isEmpty())
			throw new IllegalStateException("no engine fixtures in " + options.get("--fixtures"));

		List<Placed> survivors = new ArrayList<>();
		Path allow = options.containsKey("--allow") ? Path.of(options.get("--allow")) : null;
		boot(server, neo, "first", allow, console -> {
			console.command("forceload add 0 0 95 63");
			console.command("forceload add 0 64 63 191");
			console.command("gamerule doMobSpawning false");
			for (Placed block : blocks) {
				console.command("setblock " + (block.x) + " " + (block.y - 1) + " " + block.z + " minecraft:stone");
				String answer = console.command("setblock " + block.at() + " create_engines:" + block.id);
				if (!answer.contains("Changed the block"))
					report.fail("place", block.id, answer.strip());
			}
			for (int i = 0; i < engines.size(); i++) {
				for (String command : engines.get(i)) {
					String answer = console.command(command);
					// "Could not set" is also what a block already in place answers, so only a refusal to parse counts here;
					// whether the engine is whole shows in whether it turns
					if (answer.contains("<--[HERE]"))
						report.fail("engine", engineNames.get(i), answer.strip());
				}
			}
			Thread.sleep(15_000);
			for (Placed block : blocks)
				if (console.isThere(block))
					survivors.add(block);
				else
					report.warn("place", block.id, "did not stay where it was put (it may need support)");
			report.pass("place", survivors.size() + " of " + blocks.size() + " blocks placed and ticking for 15 s");
			checkEngines(console, "first boot", engineNames, cranks);
			console.command("save-all flush");
		});
		boot(server, neo, "second", allow, console -> {
			Thread.sleep(15_000);
			int kept = 0;
			for (Placed block : survivors)
				if (console.isThere(block))
					kept++;
				else
					report.fail("reload", block.id, "was there before the restart and is gone after it");
			report.pass("reload", kept + " of " + survivors.size() + " blocks are back after a restart");
			checkEngines(console, "after a restart", engineNames, cranks);
		});
	}

	private void checkEngines(Console console, String when, List<String> names, List<Placed> cranks)
			throws IOException, InterruptedException {
		for (int i = 0; i < names.size(); i++) {
			Placed crank = cranks.get(i);
			if (crank == null) {
				report.fail("engine", names.get(i), "the fixture has no crankshaft that records an engine");
				continue;
			}
			String answer = console.command("data get block " + crank.at() + " EngineSpeed");
			Matcher speed = NUMBER.matcher(answer);
			if (speed.find() && Math.abs(Float.parseFloat(speed.group(1))) > 1)
				report.pass("engine", names.get(i) + " turns at " + speed.group(1) + " RPM, " + when);
			else
				report.fail("engine", names.get(i), "is not turning, " + when + ": " + answer.strip());
		}
	}

	/** Makes the run folder: the install's libraries (copied once), this jar and Create alone in mods, and a new world. */
	private String prepare(Path install, Path jar, String createVersion, Path server) throws IOException {
		Path libraries = install.resolve("libraries");
		Path neoDir = libraries.resolve("net/neoforged/neoforge");
		if (!Files.isDirectory(neoDir))
			throw new IllegalStateException("no NeoForge server install at " + install.toAbsolutePath()
				+ ": run the NeoForge installer with --installServer into a folder, accept the EULA there, and pass -PserverInstall=FOLDER");
		if (!Files.isRegularFile(jar))
			throw new IllegalStateException("no mod jar at " + jar);
		Path eula = install.resolve("eula.txt");
		if (!Files.isRegularFile(eula) || !Files.readString(eula).contains("eula=true"))
			throw new IllegalStateException("the EULA is not accepted in " + install.toAbsolutePath()
				+ " (eula.txt); that is yours to accept, this task does not");
		String neo;
		try (Stream<Path> versions = Files.list(neoDir)) {
			neo = versions.map(p -> p.getFileName().toString()).sorted().findFirst()
				.orElseThrow(() -> new IllegalStateException("no NeoForge version in " + neoDir));
		}
		Path create = null;
		for (Path folder : new Path[] {install.resolve("mods"), install.resolveSibling("dl")}) {
			Path candidate = folder.resolve("create-1.21.1-" + createVersion + ".jar");
			if (Files.isRegularFile(candidate)) {
				create = candidate;
				break;
			}
		}
		if (create == null)
			throw new IllegalStateException("no create-1.21.1-" + createVersion + ".jar in the install's mods folder or ../dl");

		for (String stale : List.of("world", "mods", "config", "logs", "crash-reports", "defaultconfigs"))
			delete(server.resolve(stale));
		Files.createDirectories(server.resolve("mods"));
		if (!Files.isDirectory(server.resolve("libraries/net/neoforged/neoforge/" + neo)))
			copyTree(libraries, server.resolve("libraries"));
		Files.copy(jar, server.resolve("mods").resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(create, server.resolve("mods").resolve(create.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(eula, server.resolve("eula.txt"), StandardCopyOption.REPLACE_EXISTING);
		Path jvmArgs = install.resolve("user_jvm_args.txt");
		Files.writeString(server.resolve("user_jvm_args.txt"), Files.isRegularFile(jvmArgs) ? Files.readString(jvmArgs) : "");
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(server.resolve("server.properties"), String.join("\n", "level-type=minecraft\\:flat",
			// A flat world with no layers named logs an error and falls back to the default ones
			"generator-settings={\"biome\":\"minecraft:plains\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
				+ "{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}]}",
			"level-name=world", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "simulation-distance=4",
			"spawn-protection=0", "max-tick-time=60000", ""));
		return neo;
	}

	private interface Session {
		void play(Console console) throws IOException, InterruptedException;
	}

	private void boot(Path server, String neo, String which, Path allow, Session session) throws IOException, InterruptedException {
		String argsName = System.getProperty("os.name").startsWith("Windows") ? "win_args.txt" : "unix_args.txt";
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(java, "@user_jvm_args.txt",
			"@libraries/net/neoforged/neoforge/" + neo + "/" + argsName, "nogui")
			.directory(server.toFile()).redirectErrorStream(true).start();
		Console console = new Console(process);
		try {
			String start = console.readUntil("Done (", 300);
			if (!start.contains("(create_engines)") && !start.contains("create_engines"))
				report.fail("boot", which, "the mod list does not name create_engines");
			report.pass("boot", "the " + which + " boot reached Done");
			session.play(console);
			console.send("stop");
			long asked = System.nanoTime();
			if (!process.waitFor(STOP_SECONDS, TimeUnit.SECONDS))
				report.fail("boot", which, "the server did not stop within " + STOP_SECONDS + " s; thread dump: "
					+ threadDump(process, server.resolveSibling(which + "-stop-threads.txt")));
			else if (process.exitValue() != 0)
				report.fail("boot", which, "exit code " + process.exitValue());
			else
				report.pass("boot", "the " + which + " boot stopped in " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - asked) + " s");
		} finally {
			// Only ever this run's own process
			if (process.isAlive())
				process.destroyForcibly();
		}
		Path crashes = server.resolve("crash-reports");
		if (Files.isDirectory(crashes))
			report.fail("boot", which, "a crash report was written in " + crashes);
		Path log = server.resolve("logs/latest.log");
		if (!Files.isRegularFile(log)) {
			report.fail("log", which, "no server log");
			return;
		}
		Path kept = server.resolve("logs/" + which + "-boot.log");
		Files.copy(log, kept, StandardCopyOption.REPLACE_EXISTING);
		LogScanner scanner = new LogScanner();
		if (allow != null && Files.exists(allow))
			scanner.configure(Files.readString(allow, StandardCharsets.UTF_8));
		LogScanner.report(scanner.scan(Files.readAllLines(kept, StandardCharsets.ISO_8859_1)), report);
	}

	/** Where a server that would not stop was stuck, so a failure can be told from a slow machine. */
	private static String threadDump(Process process, Path to) throws IOException, InterruptedException {
		String jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd").toString();
		Process dump = new ProcessBuilder(jcmd, Long.toString(process.pid()), "Thread.print")
			.redirectErrorStream(true).redirectOutput(to.toFile()).start();
		if (!dump.waitFor(60, TimeUnit.SECONDS))
			dump.destroyForcibly();
		return to.toAbsolutePath().toString();
	}

	/** The server's console: commands go in, and what comes out is read on a thread so a silent server times out. */
	private static final class Console {
		private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
		private final OutputStream in;
		private static final String CLOSED = "\0closed";
		private int marks;

		Console(Process process) {
			in = process.getOutputStream();
			Thread reader = new Thread(() -> {
				try (BufferedReader out = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.ISO_8859_1))) {
					String line;
					while ((line = out.readLine()) != null)
						lines.add(line);
				} catch (IOException ignored) {
					// The pipe closes with the process
				}
				lines.add(CLOSED);
			}, "jar-server-console");
			reader.setDaemon(true);
			reader.start();
		}

		void send(String command) throws IOException {
			in.write((command + "\n").getBytes(StandardCharsets.UTF_8));
			in.flush();
		}

		String command(String command) throws IOException, InterruptedException {
			String mark = MARK + ++marks + ";";
			send(command);
			send("say " + mark);
			String answer = readUntil(mark, 120);
			// Drop the mark's own line
			int last = answer.lastIndexOf('\n', answer.length() - 2);
			return answer.substring(0, last + 1);
		}

		boolean isThere(Placed block) throws IOException, InterruptedException {
			return command("execute if block " + block.at() + " create_engines:" + block.id).contains("Test passed");
		}

		String readUntil(String wanted, int seconds) throws InterruptedException {
			StringBuilder read = new StringBuilder();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			while (true) {
				String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				if (line == null || line.equals(CLOSED)) {
					String tail = read.length() > 1500 ? read.substring(read.length() - 1500) : read.toString();
					throw new IllegalStateException((line == null ? "no \"" + wanted + "\" within " + seconds + " s"
						: "the server exited before \"" + wanted + "\"") + "; its last output:\n" + tail);
				}
				read.append(line).append('\n');
				if (line.contains(wanted))
					return read.toString();
			}
		}
	}

	/** The names of every blockstate the jar carries, which is every block the mod registers a look for. */
	static List<String> blockIds(Path jar) throws IOException {
		String prefix = "assets/create_engines/blockstates/";
		TreeSet<String> ids = new TreeSet<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			zip.stream().map(e -> e.getName()).filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
				.forEach(n -> ids.add(n.substring(prefix.length(), n.length() - ".json".length())));
		}
		return new ArrayList<>(ids);
	}

	/** Far enough apart that a spilled fluid or a neighbour update from one block does not reach the next. */
	static List<Placed> grid(List<String> ids) {
		List<Placed> placed = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++)
			placed.add(new Placed(ids.get(i), 4 + i % PER_ROW * SPACING, GROUND, 4 + i / PER_ROW * SPACING));
		return placed;
	}

	/**
	 * The commands that build a saved fixture with its corner at the given place. The structure block that recorded it is
	 * left out, and so is everything in a block entity that names the recording world (its place, its kinetic network and
	 * the blocks it was linked to), which the game works out again.
	 *
	 * @param cranks filled with the block that recorded an engine's speed, if there is one
	 */
	static List<String> fixtureCommands(String snbt, int originX, int originY, int originZ, List<Placed> cranks) {
		List<String> commands = new ArrayList<>();
		for (String element : Snbt.list(Snbt.compound(snbt).get("Blocks"))) {
			Map<String, String> block = Snbt.compound(element);
			Map<String, String> state = Snbt.compound(block.get("State"));
			String name = Snbt.unquote(state.get("Name"));
			if (name.equals("minecraft:structure_block") || name.equals("minecraft:air"))
				continue;
			List<String> pos = Snbt.list(block.get("Pos"));
			int x = originX + Integer.parseInt(pos.get(0).trim());
			int y = originY + Integer.parseInt(pos.get(1).trim());
			int z = originZ + Integer.parseInt(pos.get(2).trim());
			StringBuilder command = new StringBuilder("setblock " + x + " " + y + " " + z + " " + name);
			if (state.containsKey("Properties")) {
				List<String> properties = new ArrayList<>();
				Snbt.compound(state.get("Properties")).forEach((k, v) -> properties.add(k + "=" + Snbt.unquote(v)));
				command.append('[').append(String.join(",", properties)).append(']');
			}
			if (block.containsKey("Entity")) {
				Map<String, String> entity = Snbt.compound(block.get("Entity"));
				if (name.equals("create_engines:crankshaft") && entity.containsKey("EngineSpeed")
						&& Float.parseFloat(entity.get("EngineSpeed").replace("f", "")) > 0)
					cranks.add(new Placed("crankshaft", x, y, z));
				for (String local : List.of("x", "y", "z", "id", "Network", "Source", "Primary", "Speed"))
					entity.remove(local);
				List<String> kept = new ArrayList<>();
				entity.forEach((k, v) -> kept.add(k + ":" + v));
				command.append('{').append(String.join(",", kept)).append('}');
			}
			commands.add(command.toString());
		}
		return commands;
	}

	private static void delete(Path path) throws IOException {
		if (!Files.exists(path))
			return;
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException failed) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> all = Files.walk(from)) {
			for (Path source : all.toList()) {
				Path target = to.resolve(from.relativize(source).toString());
				if (Files.isDirectory(source))
					Files.createDirectories(target);
				else
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}

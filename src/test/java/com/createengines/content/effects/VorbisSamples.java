package com.createengines.content.effects;

import java.util.ArrayList;
import java.util.List;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

/** A mono Ogg Vorbis recording decoded to samples, -1 to 1 and a little past, with the decoder the game itself uses. */
public final class VorbisSamples {

	private VorbisSamples() {
	}

	public static float[] decode(byte[] bytes) {
		SyncState sync = new SyncState();
		sync.init();
		int at = sync.buffer(bytes.length);
		System.arraycopy(bytes, 0, sync.data, at, bytes.length);
		sync.wrote(bytes.length);

		Page page = new Page();
		Packet packet = new Packet();
		StreamState stream = new StreamState();
		Info info = new Info();
		Comment comment = new Comment();
		info.init();
		comment.init();
		DspState dsp = new DspState();
		Block block = null;
		int headers = 0;
		boolean started = false;
		List<float[]> chunks = new ArrayList<>();
		int total = 0;
		long end = 0;
		while (sync.pageout(page) == 1) {
			if (!started) {
				stream.init(page.serialno());
				started = true;
			}
			stream.pagein(page);
			if (page.granulepos() > 0)
				end = page.granulepos();
			while (stream.packetout(packet) == 1) {
				if (headers < 3) {
					if (info.synthesis_headerin(comment, packet) < 0)
						throw new IllegalArgumentException("not Vorbis");
					if (++headers == 3) {
						dsp.synthesis_init(info);
						block = new Block(dsp);
					}
					continue;
				}
				if (block.synthesis(packet) == 0)
					dsp.synthesis_blockin(block);
				float[][][] pcm = new float[1][][];
				int[] index = new int[info.channels];
				int ready;
				while ((ready = dsp.synthesis_pcmout(pcm, index)) > 0) {
					float[] chunk = new float[ready];
					System.arraycopy(pcm[0][0], index[0], chunk, 0, ready);
					chunks.add(chunk);
					total += ready;
					dsp.synthesis_read(ready);
				}
			}
		}
		// The last page says where the sound ends; the blocks run past it
		float[] samples = new float[(int) Math.min(total, end)];
		int filled = 0;
		for (float[] chunk : chunks) {
			int take = Math.min(chunk.length, samples.length - filled);
			System.arraycopy(chunk, 0, samples, filled, take);
			filled += take;
		}
		return samples;
	}
}

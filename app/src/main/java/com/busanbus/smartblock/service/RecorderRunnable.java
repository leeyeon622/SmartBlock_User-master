package com.busanbus.smartblock.service;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class RecorderRunnable implements Runnable {
	private static String LOG_TAG = "RecorderRunnable";

	public final int maxTime = 60; // in seconds
	public final int readSamples = 0x100;
	public final int BUFFER_SIZE = 0x1000;
	private static int CHANNEL_MODE = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	public final int totalSamples;
	public final int fftChunkSize;
	public int mRate;
	public Handler handler;

	private final static int MSG_ERR = 1;
	private final static int MSG_RST = 0;
	private final static int MSG_EXC = -10;

	private short[] mAudioData;
	private int filled;
	private ReentrantLock audioReadLock;
	private AudioRecord mRecorder;
	private int readed;

	private BlockingQueue<Integer> queue;


	public RecorderRunnable(BlockingQueue<Integer> queue_, int rate,
                            int fftChunkSize) {
		super();
		queue = queue_;
		totalSamples = maxTime * rate;
		this.mRate = rate;
		this.fftChunkSize = fftChunkSize;

        Log.d(LOG_TAG, "RecorderRunnable : rate : " + rate + " : fftChunkSize : " + fftChunkSize + " : totalSamples : " + totalSamples);

		audioReadLock = new ReentrantLock();
	}

	public short[] get(int howMany) {
		short[] audioBuffer = new short[howMany];

		//Log.d(LOG_TAG, "get : howMany : " + howMany + " : filled : " + filled + " : readed : " + readed);

		audioReadLock.lock();
		try {
			System.arraycopy(mAudioData, filled - howMany, audioBuffer, 0,
					howMany);
		} finally {
			audioReadLock.unlock();
			readed += howMany;
		}

		return audioBuffer;
	}

	public short[] getLatest(int howMany) throws InterruptedException {
		// wait so you get something new and not something old twice.

        //Log.d(LOG_TAG, "getLatest : howMany : " + howMany + " : filled : " + filled + " : readed : " + readed);

		queue.take();

        //Log.d(LOG_TAG, "getLatest : after take : howMany : " + howMany);

		if ((filled - readed) < howMany) {
			while (true) {
				queue.take();
				if ((filled - readed) >= howMany) {
					// got enough stuff to give back.
					break;
				}
			}
		}

		// empty the queue now so we don't have it fill up needlessly.
		// A better solution would be a lock and not a queue, maybe a TODO.

		while (null != queue.poll()) {
			// nop
		}

		return get(howMany);

	}

	public void reset() {

        //Log.d(LOG_TAG, "reset : filled : " + filled + " : readed : " + readed);

		audioReadLock.lock();
		try {
			System.arraycopy(mAudioData, readed, mAudioData, 0,
					filled - readed);
			filled = filled - readed;
			readed = 0;
		} finally {
			audioReadLock.unlock();
		}
	}

	public boolean readLoop() throws InterruptedException {
		int res;

        Log.d(LOG_TAG, "readLoop");

		while (!Thread.interrupted()) {
			if (filled + readSamples > totalSamples) {
				reset();
			}

			res = mRecorder.read(mAudioData, filled, readSamples);

            //Log.d(LOG_TAG, "readLoop : res : " + res);

			if ((res == AudioRecord.ERROR_INVALID_OPERATION)
					|| (res == AudioRecord.ERROR_BAD_VALUE)) {
				Log.e(LOG_TAG, "readLoop : audio record failed : " + res);
				return false;
			}

			if (res == 0) {
				Log.e(LOG_TAG, "readLoop : audio record failed reading - zero buffer");
				Thread.sleep(500);
				return false;
			}

			filled += res;

            //Log.d(LOG_TAG, "readLoop : filled : " + filled);

			queue.put(res);
		}

		return false;
	}

	@Override
	public void run() {
		boolean retry = true;

		while (retry) {
			retry = false;
			filled = 0;
			readed = 0;
			mAudioData = new short[totalSamples];

			android.os.Process
					.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			int bufferSize = BUFFER_SIZE;
			int minBufferSize = AudioRecord.getMinBufferSize(mRate,
					CHANNEL_MODE, ENCODING);
			if (minBufferSize > bufferSize) {
				bufferSize = minBufferSize;
			}
			Log.d(LOG_TAG, "run : recording buffer size: " + bufferSize);

			mRecorder = new AudioRecord(AudioSource.MIC, mRate, CHANNEL_MODE,
					ENCODING, bufferSize);

			if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
				break;
			}

			mRecorder.startRecording();
            Log.d(LOG_TAG, "run : startRecording");

			try {
				retry = readLoop();
			} catch (InterruptedException e) {
				Log.d(LOG_TAG, "run : recording interrupted.");
				e.printStackTrace();
				return;
			} finally {
				Log.d(LOG_TAG, "run : RecorderRunnable stopping recording.");
				mRecorder.stop();
				mRecorder.release();
			}
		}
	}


}

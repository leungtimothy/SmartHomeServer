package elec291group2.com.SmartHomeServer;

import java.io.File;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Alarm implements Runnable {
	private volatile boolean isRunning = false;

	/*
	 * public static void main(String[] args){ Alarm a = new Alarm(); Thread t =
	 * new Thread(a); t.start(); a.start();
	 * 
	 * a.stop(); }
	 */

	public void start() {
		isRunning = true;
	}

	public void stop() {
		isRunning = false;
	}

	@Override
	public void run() {
		AudioInputStream audioIn = null;
		Clip clip = null;
		try {
			audioIn = AudioSystem.getAudioInputStream(new File("Alarm.wav"));
			clip = AudioSystem.getClip();
			clip.open(audioIn);
		} catch (Exception e) {
			e.printStackTrace();
		}
		clip.start();
		clip.loop(Clip.LOOP_CONTINUOUSLY);
		while(isRunning){}
		clip.loop(0);
		clip.stop();
		//clip.close();
		System.out.println("alarm thread confirmed kill");
	}
}

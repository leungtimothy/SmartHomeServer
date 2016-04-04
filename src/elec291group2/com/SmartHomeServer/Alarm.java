package elec291group2.com.SmartHomeServer;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Alarm implements Runnable {
	private Thread t;
	private volatile boolean isRunning;
	
	/*
    public static void main(String[] args){
    	Alarm a = new Alarm();
    	Thread t = new Thread(a);
    	t.start();
    	a.start();

    	a.stop();
    }*/
    
	public void start(){
		isRunning = true;
	}
	
	public void stop(){
		isRunning = false;
	}
	
    @Override
    public void run() {
        AudioInputStream audioIn;
        while (isRunning) {
	        try {
	            audioIn = AudioSystem.getAudioInputStream(new File("Alarm.wav"));
	            Clip clip = AudioSystem.getClip();
	            clip.open(audioIn);
	            clip.start();
	            Thread.sleep(clip.getMicrosecondLength()/1000-25);
	        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException  e1) {
	            e1.printStackTrace();
	        }
        }
    }
}
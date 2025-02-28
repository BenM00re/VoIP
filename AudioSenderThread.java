import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.*;
import java.nio.ByteBuffer;

import CMPC3M06.AudioRecorder;

import javax.sound.sampled.LineUnavailableException;

public class AudioSenderThread implements Runnable{

    static DatagramSocket sending_socket;

    public void start(){
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run (){
        InetAddress clientIP = null;
        try {
            clientIP = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        int PORT = 55555;
        AudioRecorder recorder = null;
        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        try {
            sending_socket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        boolean running = true;
            byte[] key = "ThisIsAComplexKey123".getBytes();
            int keyIndex = 0;
            while(running) {
                try {
                    byte[] block = recorder.getBlock();
                    byte[] encryptedBlock = encrypt(block, key); // Encrypt the block
                    DatagramPacket packet = new DatagramPacket(encryptedBlock, encryptedBlock.length, clientIP, PORT);
                    sending_socket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                    running = false;
                }
            }
            sending_socket.close();
            recorder.close();
        }
        private static byte[] encrypt(byte[] data, byte[] key) {
            byte[] encrypted = new byte[data.length];
            int keyIndex = 0;

            for (int i = 0; i < data.length; i++) {
                encrypted[i] = (byte) (data[i] ^ key[keyIndex]);
                keyIndex = (keyIndex + 1) % key.length; // Cycle through the key
            }

            return encrypted;
        }
}


import CMPC3M06.AudioPlayer;

import javax.sound.sampled.LineUnavailableException;
import java.net.DatagramSocket;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioReceiverThread implements Runnable{

    static DatagramSocket receiving_socket;

    public void start(){
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run (){
        int PORT= 55555;
        AudioPlayer player = null;
        try {
            player = new AudioPlayer();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        try {
            receiving_socket = new DatagramSocket(PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        boolean running=true;
        byte[] key = "ThisIsAComplexKey123".getBytes();
        int keyIndex = 0;
        while(running) {
            try{
                byte[] block = new byte[512];
                DatagramPacket packet = new DatagramPacket(block, block.length);
                receiving_socket.receive(packet);
                byte[] decryptedBlock = decrypt(block, key);
                player.playBlock(decryptedBlock);
            } catch (Exception e) {
                e.printStackTrace();
                running=false;
            }
        }
        receiving_socket.close();
        player.close();
    }
    // Improved XOR decryption
    private static byte[] decrypt(byte[] data, byte[] key) {
        return encrypt(data, key); // XOR decryption is the same as encryption
    }
    // XOR encryption method (same as in AudioSender)
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


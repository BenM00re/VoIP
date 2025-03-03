import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.*;
import java.nio.ByteBuffer;

import CMPC3M06.AudioRecorder;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket4;

import javax.sound.sampled.LineUnavailableException;

public class AudioSenderThread implements Runnable {
    static DatagramSocket sending_socket;
    private int packetCount = 0; // Counter for packets

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        InetAddress clientIP;
        try {
            clientIP = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        int PORT = 55555;
        AudioRecorder recorder;

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
        byte encryptionKey = 0x5A; // Simple fixed XOR key

        while (running) {
            try {
                byte[] block = recorder.getBlock();
                byte[] encryptedBlock = encrypt(block, encryptionKey); // Encrypt using simple XOR

                ByteBuffer VoIPpacket = ByteBuffer.allocate(514);
                //short authenticationKey = (short) ((packetCount % 5 == 0) ? 99 : 10); // Every 5th packet is invalid
                short authenticationKey = 10; //stable
                packetCount++;

                VoIPpacket.putShort(authenticationKey);
                VoIPpacket.put(encryptedBlock);

                DatagramPacket packet = new DatagramPacket(VoIPpacket.array(), 514, clientIP, PORT);
                sending_socket.send(packet);

                System.out.println("Sent packet #" + packetCount + " with authentication key: " + authenticationKey);

            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        sending_socket.close();
        recorder.close();
    }

    // Simplified XOR encryption (single-byte key)
    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key); // XOR each byte with the key
        }
        return encrypted;
    }
}

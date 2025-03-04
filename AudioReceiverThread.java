import CMPC3M06.AudioPlayer;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket4;

import javax.sound.sampled.LineUnavailableException;
import java.net.DatagramSocket;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioReceiverThread implements Runnable {
    static DatagramSocket receiving_socket;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        int PORT = 55555;
        AudioPlayer player;

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

        boolean running = true;
        byte encryptionKey = 0x5A; // Same XOR key as sender
        int receivedPacketCount = 0;

        while (running) {
            try {
                byte[] packetData = new byte[514]; // 514 bytes (2 for header + 512 for payload)
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                receiving_socket.receive(packet);
                receivedPacketCount++;

                ByteBuffer VoIPpacket = ByteBuffer.wrap(packet.getData());
                short receivedKey = VoIPpacket.getShort();

                if (receivedKey != 10) {
                    System.out.println("DEBUG: Rejected packet #" + receivedPacketCount + " with invalid authentication key: " + receivedKey);
                    continue;
                }

                byte[] encryptedBlock = new byte[512];
                VoIPpacket.get(encryptedBlock);

                byte[] decryptedBlock = decrypt(encryptedBlock, encryptionKey);
                player.playBlock(decryptedBlock);

                System.out.println("DEBUG: Received and played packet #" + receivedPacketCount + " with authentication key: " + receivedKey);

            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        receiving_socket.close();
        player.close();
    }

    // XOR decryption (same as encryption)
    private static byte[] decrypt(byte[] data, byte key) {
        return encrypt(data, key); // XOR is reversible
    }

    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }
}


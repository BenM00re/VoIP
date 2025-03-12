import java.io.IOException;
import java.net.InetAddress;
import java.net.*;
import java.nio.ByteBuffer;
import CMPC3M06.AudioRecorder;
import java.net.DatagramSocket;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket4;


import javax.sound.sampled.LineUnavailableException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioSenderThread implements Runnable {
    static DatagramSocket sending_socket;
    private int PacketCount = 0;
    private int SequenceNumber = 0;
    private static final int InterleaveBufferSize = 10;


    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        InetAddress clientIP;
        try {
            clientIP = InetAddress.getByName(Address.ipAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        int PORT = Address.PORT;
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
        byte encryptionKey = 0x5A;
        List<ByteBuffer> interleaveBuffer = new ArrayList<>();

        while (running) {
            try {
                byte[] block = recorder.getBlock();
                byte[] encryptedBlock = encrypt(block, encryptionKey);

                ByteBuffer VoIPpacket = ByteBuffer.allocate(518); //4 more for sequence numbers
                short authenticationKey = 10;
                PacketCount++;

                VoIPpacket.putInt(SequenceNumber);  // Add sequence number
                VoIPpacket.putShort(authenticationKey);
                VoIPpacket.put(encryptedBlock);

                interleaveBuffer.add(VoIPpacket);

                //Interleave and send when the buffer is full
                if (interleaveBuffer.size() >= InterleaveBufferSize) {
                    List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);

                    //Debugging
                    System.out.print("For Debugging: Sender - packets BEFORE interleaving: [");
                    for (ByteBuffer p : interleaveBuffer) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");

                    System.out.print("For Debugging: Sender - packets AFTER interleaving: [");
                    for (ByteBuffer p : interleavedPackets) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");



                    for (ByteBuffer packetToSend : interleavedPackets) {
                        DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                        sending_socket.send(packet);
                        System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));
                    }
                    interleaveBuffer.clear();
                }


                SequenceNumber++;
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        //Send any remaining packets
        if(!interleaveBuffer.isEmpty()){
            List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);
            for (ByteBuffer packetToSend : interleavedPackets) {
                try {
                    DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                    sending_socket.send(packet);
                    System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));

                } catch (IOException e){
                    System.err.println("Error sending the remaining packets");
                }
            }
            interleaveBuffer.clear();
        }

        sending_socket.close();
        recorder.close();
    }

    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }

    private List<ByteBuffer> interleave(List<ByteBuffer> packets) {
        Collections.shuffle(packets);
        return packets;
    }
}

class AudioSenderThread2 implements Runnable {
    static DatagramSocket2 sending_socket;
    private int PacketCount = 0;
    private int SequenceNumber = 0;
    private static final int InterleaveBufferSize = 10;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        InetAddress clientIP;
        try {
            clientIP = InetAddress.getByName(Address.ipAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        int PORT = Address.PORT;
        AudioRecorder recorder;

        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        try {
            sending_socket = new DatagramSocket2();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        List<ByteBuffer> interleaveBuffer = new ArrayList<>();

        while (running) {
            try {
                byte[] block = recorder.getBlock();
                byte[] encryptedBlock = encrypt(block, encryptionKey);

                ByteBuffer VoIPpacket = ByteBuffer.allocate(518); //4 more for sequence numbers
                short authenticationKey = 10;
                PacketCount++;

                VoIPpacket.putInt(SequenceNumber);  // Add sequence number
                VoIPpacket.putShort(authenticationKey);
                VoIPpacket.put(encryptedBlock);

                interleaveBuffer.add(VoIPpacket);

                //Interleave and send when the buffer is full
                if (interleaveBuffer.size() >= InterleaveBufferSize) {
                    List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);

                    //Debugging
                    System.out.print("For Debugging: Sender - packets BEFORE interleaving: [");
                    for (ByteBuffer p : interleaveBuffer) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");

                    System.out.print("For Debugging: Sender - packets AFTER interleaving: [");
                    for (ByteBuffer p : interleavedPackets) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");



                    for (ByteBuffer packetToSend : interleavedPackets) {
                        DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                        sending_socket.send(packet);
                        System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));
                    }
                    interleaveBuffer.clear();
                }


                SequenceNumber++;
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        //Send any remaining packets
        if(!interleaveBuffer.isEmpty()){
            List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);
            for (ByteBuffer packetToSend : interleavedPackets) {
                try {
                    DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                    sending_socket.send(packet);
                    System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));

                } catch (IOException e){
                    System.err.println("Error sending the remaining packets");
                }
            }
            interleaveBuffer.clear();
        }

        sending_socket.close();
        recorder.close();
    }

    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }

    private List<ByteBuffer> interleave(List<ByteBuffer> packets) {
        Collections.shuffle(packets);
        return packets;
    }
}

class AudioSenderThread3 implements Runnable {
    static DatagramSocket3 sending_socket;
    private int PacketCount = 0;
    private int SequenceNumber = 0;
    private static final int InterleaveBufferSize = 10;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        InetAddress clientIP;
        try {
            clientIP = InetAddress.getByName(Address.ipAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        int PORT = Address.PORT;
        AudioRecorder recorder;

        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        try {
            sending_socket = new DatagramSocket3();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        List<ByteBuffer> interleaveBuffer = new ArrayList<>();

        while (running) {
            try {
                byte[] block = recorder.getBlock();
                byte[] encryptedBlock = encrypt(block, encryptionKey);

                ByteBuffer VoIPpacket = ByteBuffer.allocate(518); //4 more for sequence numbers
                short authenticationKey = 10;
                PacketCount++;

                VoIPpacket.putInt(SequenceNumber);  // Add sequence number
                VoIPpacket.putShort(authenticationKey);
                VoIPpacket.put(encryptedBlock);

                interleaveBuffer.add(VoIPpacket);

                //Interleave and send when the buffer is full
                if (interleaveBuffer.size() >= InterleaveBufferSize) {
                    List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);

                    //Debugging
                    System.out.print("For Debugging: Sender - packets BEFORE interleaving: [");
                    for (ByteBuffer p : interleaveBuffer) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");

                    System.out.print("For Debugging: Sender - packets AFTER interleaving: [");
                    for (ByteBuffer p : interleavedPackets) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");



                    for (ByteBuffer packetToSend : interleavedPackets) {
                        DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                        sending_socket.send(packet);
                        System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));
                    }
                    interleaveBuffer.clear();
                }


                SequenceNumber++;
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        //Send any remaining packets
        if(!interleaveBuffer.isEmpty()){
            List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);
            for (ByteBuffer packetToSend : interleavedPackets) {
                try {
                    DatagramPacket packet = new DatagramPacket(packetToSend.array(), 518, clientIP, PORT);
                    sending_socket.send(packet);
                    System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));

                } catch (IOException e){
                    System.err.println("Error sending the remaining packets");
                }
            }
            interleaveBuffer.clear();
        }

        sending_socket.close();
        recorder.close();
    }

    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }

    private List<ByteBuffer> interleave(List<ByteBuffer> packets) {
        Collections.shuffle(packets);
        return packets;
    }
}

class AudioSenderThread4 implements Runnable {
    static DatagramSocket4 sending_socket;
    private int PacketCount = 0;
    private int SequenceNumber = 0;
    private static final int InterleaveBufferSize = 10;
    private static final byte encryptionKey = 0x5A;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        InetAddress clientIP;
        try {
            clientIP = InetAddress.getByName(Address.ipAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        int PORT = Address.PORT;
        AudioRecorder recorder;

        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        try {
            sending_socket = new DatagramSocket4();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        boolean running = true;
        List<ByteBuffer> interleaveBuffer = new ArrayList<>();

        while (running) {
            try {
                byte[] block = recorder.getBlock();
                byte[] encryptedBlock = encrypt(block, encryptionKey);

                short checksum = calculateChecksum(encryptedBlock);

                ByteBuffer VoIPpacket = ByteBuffer.allocate(520); //4 more for sequence numbersVoIPpacket.putInt(SequenceNumber);  // Add sequence number
                VoIPpacket.putInt(SequenceNumber);
                VoIPpacket.putShort((short) 10);
                VoIPpacket.putShort(checksum);
                VoIPpacket.put(encryptedBlock);


                interleaveBuffer.add(VoIPpacket);

                //Interleave and send when the buffer is full
                if (interleaveBuffer.size() >= InterleaveBufferSize) {
                    List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);

                    //Debugging
                    System.out.print("For Debugging: Sender - packets BEFORE interleaving: [");
                    for (ByteBuffer p : interleaveBuffer) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");

                    System.out.print("For Debugging: Sender - packets AFTER interleaving: [");
                    for (ByteBuffer p : interleavedPackets) {
                        System.out.print(p.getInt(0) + ", ");
                    }
                    System.out.println("]");



                    for (ByteBuffer packetToSend : interleavedPackets) {
                        DatagramPacket packet = new DatagramPacket(packetToSend.array(), 520, clientIP, PORT);
                        sending_socket.send(packet);
                        System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(10));
                    }
                    interleaveBuffer.clear();
                }


                SequenceNumber++;
                PacketCount++;
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }

        //Send any remaining packets
        if(!interleaveBuffer.isEmpty()){
            List<ByteBuffer> interleavedPackets = interleave(interleaveBuffer);
            for (ByteBuffer packetToSend : interleavedPackets) {
                try {
                    DatagramPacket packet = new DatagramPacket(packetToSend.array(), 520, clientIP, PORT);
                    sending_socket.send(packet);
                    System.out.println("Sent packet #" + PacketCount + " with sequence number: " + packetToSend.getInt(0) + " and authentication key: " + packetToSend.getShort(4));

                } catch (IOException e){
                    System.err.println("Error sending the remaining packets");
                }
            }
            interleaveBuffer.clear();
        }

        sending_socket.close();
        recorder.close();
    }

    private short calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += b & 0xFF; // converts byte to an unsigned equivalent by mask
        }
        return (short) (sum & 0xFFFF); // return checksum
    }

    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }

    private List<ByteBuffer> interleave(List<ByteBuffer> packets) {
        Collections.shuffle(packets);
        return packets;
    }
}

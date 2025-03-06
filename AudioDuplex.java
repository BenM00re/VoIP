public class AudioDuplex {

    public static void main(String[] args) {
        try {
            //RECEIVERS
            AudioReceiverThread receiver = new AudioReceiverThread();
            AudioReceiverThread2 receiver2 = new AudioReceiverThread2();
            AudioReceiverThread3 receiver3 = new AudioReceiverThread3();
            AudioReceiverThread4 receiver4 = new AudioReceiverThread4();
            //SENDERS
            AudioSenderThread sender = new AudioSenderThread();
            AudioSenderThread2 sender2 = new AudioSenderThread2();
            AudioSenderThread3 sender3 = new AudioSenderThread3();
            AudioSenderThread4 sender4 = new AudioSenderThread4();

            //CHANGE NUMBER BASED ON SOCKET
            receiver.start();
            sender.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

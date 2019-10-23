package Client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Chat extends Thread {

    private String user;
    private InetAddress ia;
    private boolean isRunning;
    private MulticastSocket ms;
    private ConcurrentLinkedQueue<String> queue;

    public Chat(String user, InetAddress ia,MulticastSocket ms,ConcurrentLinkedQueue queue){
        this.user = user;
        this.ia= ia;
        this.ms = ms;
        this.queue = queue;
        isRunning = true;
    }

    public void run(){

        byte[] buffer = new byte[512];
        DatagramPacket dp;
        Calendar cal =  Calendar.getInstance(TimeZone.getDefault());
        int ora = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);

        String message = "["+ora+":"+min+"] Utente "+user+" collegato!";

        dp = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8), 0,
                message.getBytes(StandardCharsets.UTF_8).length,ia,4400);
        try {
            ms.joinGroup(ia);
            ms.send(dp);

            while(isRunning){
                DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
                ms.receive(packet);
                queue.add(new String(packet.getData(),0,packet.getLength(),"UTF-8"));
            }
            ms.leaveGroup(ia);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void shutDown(){
        isRunning = false;

    }
}

package Server;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY )
public class User {

    private String username;
    private String password;
    private Vector<String> userDoc;
    private ConcurrentLinkedQueue<String> msgList;
    private int lastDocIndex;
    private String lastSectionIndex;

    @JsonCreator
    public User(@JsonProperty("username") String username,@JsonProperty("password") String password){
        this.username = username;
        this.password = password;
        this.userDoc = new Vector<String>();
        this.msgList = new ConcurrentLinkedQueue<>();
        this.lastSectionIndex = "-1";
        this.lastDocIndex = -1;
    }


    public boolean checkIdentity(String passw){
        if(this.password.equals(passw)) return true;
        else return false;
    }

    public void addDocument(String document){
        //Aggiungo il Documento alla lista dei documenti dell'utente;
        userDoc.add(document);
    }

    @JsonIgnore
    public Vector<String> getList(){
      return userDoc;
    }

    public String getUsername(){
        return username;
    }

    public void addMsg(String msg){

        System.out.println(msg);
        msgList.add(msg);

    }

    @JsonIgnore
    public String getMsg(){
        return msgList.poll();
    }

    public int getLastDocIndex(){
        return lastDocIndex;
    }

    public String getLastSectionIndex(){
        return lastSectionIndex;
    }

    public void setLastDocIndex(int index){
        lastDocIndex = index;
    }

    public void setLastSectionIndex(String index){
        lastSectionIndex = index;
    }

}

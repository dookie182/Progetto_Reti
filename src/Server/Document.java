package Server;

import Exceptions.AlreadyInvitedUserException;
import Exceptions.EditingException;
import Exceptions.ExistingDocumentException;
import Exceptions.FormatException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY )
public class Document {

    private final String creator;
    private String name;
    private Vector<String> allowedUsers;
    private String numSezioni;


    @JsonIgnore
    private Boolean[] isEditing;

    @JsonCreator
    public Document(@JsonProperty("creator") String creator, @JsonProperty("numSezioni") String numSezioni, @JsonProperty("name") String name) throws FormatException {
        this.creator = creator;
        this.allowedUsers = new Vector<String>();
        this.name = name;
        this.numSezioni = numSezioni;
        try {
            isEditing = new Boolean[Integer.parseInt(numSezioni)];
        }catch(NumberFormatException e){
            throw new FormatException("Inserire il numero delle sezioni in formato numerico!");
        }
        for (int i = 0; i < Integer.parseInt(numSezioni); i++) {
            isEditing[i] = false;
        }

    }

    public void create() throws ExistingDocumentException {
        File doc = (new File(name));

        int sezioni = Integer.parseInt(numSezioni);

        //Controllo che il documento non sia già esistente;
        if (doc.exists()) throw new ExistingDocumentException();

        if (doc.mkdirs()) {
            for (int i = 0; i < sezioni; i++) {
                File section = (new File(name + "/" + i + ".txt"));
                try {
                    section.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int getNumSezioni() {
        return Integer.parseInt(numSezioni);
    }

    public void addAllowedUser(String user) throws AlreadyInvitedUserException {
        //TODO:CONTRALLARE CHE NON SIA GIA' PRESENTE
        if (allowedUsers.indexOf(user) >= 0) throw new AlreadyInvitedUserException();

        this.allowedUsers.add(user);

    }

    public String getCreator() {
        return creator;
    }

    public String getName() {
        return name;
    }

    public void setIsEditing(String section) throws EditingException {
        if (isEditing[Integer.parseInt(section)]) {
            throw new EditingException("Qualcuno sta gia' modificando questa sezione");
        } else isEditing[Integer.parseInt(section)] = true;
    }

    public void endEditing(String section) throws EditingException {

        int sezione = Integer.parseInt(section);

        if (!isEditing[sezione]) {
            throw new EditingException("Nessun editing in corso su questa sezione!");
        } else isEditing[sezione] = false;
    }

    public boolean checkAllowedUser(String user) {

        for (String username : allowedUsers) {
            if (username.equals(user))
                return true;
        }
        return false;
    }

    public boolean isEditing(int section) {

        if (isEditing[section])
            return true;
        else
            return false;
    }

    @JsonIgnore
    public String getAllowedUsers(){
        return allowedUsers.toString();
    }
}

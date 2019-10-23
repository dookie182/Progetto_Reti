package Exceptions;

public class AlreadyInvitedUserException extends Exception {

    public AlreadyInvitedUserException(){
        super("Utente gia' invitato alla modifica!");
    }
}

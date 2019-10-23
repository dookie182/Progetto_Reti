package Exceptions;

public class OfflineException extends Exception {
    public OfflineException(){
        super("Non hai ancora effettuato il login!");
    }
}

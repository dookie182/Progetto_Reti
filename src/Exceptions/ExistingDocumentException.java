package Exceptions;

public class ExistingDocumentException extends Exception{

    public ExistingDocumentException(){
        super("Documento gia' esistente!");
    }
}

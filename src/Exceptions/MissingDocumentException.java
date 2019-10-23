package Exceptions;

public class MissingDocumentException extends Exception {

    public MissingDocumentException(){
        super("Documento non esistente!");
    }
}

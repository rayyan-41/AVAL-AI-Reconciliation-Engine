package aval.parser;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Parser Interface, covers UC2, UC3

import java.util.List;

//@desc:   Generic interface defining the Template Method algorithm skeleton for all file-parsing operations.
//@grasp:  Polymorphism
//@gof:    Template Method
public interface DocumentParser<T> {
    T parse(String filePath);
    boolean validate(String filePath);
    String getSupportedFormat();
    List<String[]> extractRawRows(String filePath);
}
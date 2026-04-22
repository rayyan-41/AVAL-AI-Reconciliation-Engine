package aval.parser;

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

//Dev: Safwan
//Use Cases: UC2, UC3
package aval.parser;

//@desc:   Generic interface defining the Template Method algorithm skeleton for all file-parsing operations.
//@grasp:  Polymorphism
//@gof:    Template Method
public interface DocumentParser<T> {
    T parse(String filePath);
    boolean validate(String filePath);
}

import re

with open('src/main/resources/aval/ui/views/Auth.fxml', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('<HBox styleClass="auth-wordmark">', '<HBox styleClass="auth-wordmark" alignment="BOTTOM_LEFT">')
c = re.sub(r'<HBox spacing="8" alignment="CENTER_LEFT"[^>]*>\s*<Label text="[^"]*"[^>]*/>\s+<Label text="([^"]*)" styleClass="auth-body"\s*/>\s*</HBox>', r'<Label text="â€¢ \1" styleClass="auth-body" style="-fx-padding: 5 0 5 0;" />', c)
c = re.sub(r'<HBox spacing="8" alignment="CENTER_LEFT"[^>]*>\s*<Label text="[^"]*"[^>]*></Label>\s+<Label text="([^"]*)" styleClass="auth-body"\s*/>\s+</HBox>', r'<Label text="â€¢ \1" styleClass="auth-body" style="-fx-padding: 5 0 5 0;" />', c)
c = c.replace('Ã¢Œ‚ç¢Ò¢Â¢Ò¢("n*"‚&â¢("n*"‚', 'â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢')

if '<StackPane' not in C:
    c = re.sub(r'<HBox[^>]*fx:controller="aval\.ui\.controller\.AuthController"[^>]*>', '<StackPane xmlns="http://javafx.com/javafx/17" xmlns:fx="http://javafx.com/fxml/1" fx:controller="aval.ui.controller.AuthController">\n<HBox prefWidth="1200" prefHeight="800" fx:id="rootNode">', c)
    c = re.sub(r'</HBox>\s*$', '</HBox>\n<Button text="â˜•" onAction="#handleClose" StackPane.alignment="TOP_RIGHT" style="-fx-background-color: transparent; -fx-font-size: 20px; -fx-text-fill: #666666; -fx-cursor: hand; -fx-padding: 10 20;" />\n</StackPane>', c)

with open('src/main/resources/aval/ui/views/Auth.fxml', 'w', encoding='utf-8') as f:
    f.write(c)

with open('src/main/resources/aval/ui/views/Register.fxml', 'r', encoding='utf-8') as f:
    c2 = f.read()
c2 = c2.replace('<HBox styleClass="auth-wordmark">', '<HBox styleClass="auth-wordmark" alignment="BOTTOM_LEFT">')
if '<StackPane' not in c2:
    c2 = re.sub(r'<HBox[^>]*fx:controller="aval\.ui\.controller\.RegisterController"[^>]*>', '<StackPane xmlns="http://javafx.com/javafx/17" xmlns:fx="http://javafx.com/fxml/1" fx:controller="aval.ui.controller.RegisterController">\n<HBox prefWidth="1200" prefHeight="800" fx:id="rootNode">', c2)
    c2 = re.sub(r'</HBox>\s*$', '</HBox>\n<Button text="â˜•" onAction="#handleClose" StackPane.alignment="TOP_RIGHT" style="-fx-background-color: transparent; -fx-font-size: 20px; -fx-text-fill: #666666; -fx-cursor: hand; -fx-padding: 10 20;" />\n</StackPane>', c2)

with open('src/main/resources/aval/uˆ½Ù¥•İÌ½I•¥ÍÑ•È¹™áµ°œ°€Üœ°•¹½‘¥¹œôÕÑ˜´àœ¤…Ì˜è(€€€˜¹İÉ¥Ñ”¡ŒÈ¤(
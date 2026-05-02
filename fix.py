import re
def fix_fxml(path, ctrl_name):
    with open(path, 'r', encoding='utf-8') as f: c = f.read()
    c = re.sub(r'u2022 ', '• ', c)
    c = c.replace('Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢', '••••••••')
    c = c.replace('â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢', '••••••••')
    if '<StackPane xmlns' not in c:
    with open(path, 'w', encoding='utf-8') as f: f.write(c)
fix_fxml('src/main/resources/aval/ui/views/Auth.fxml', 'aval.ui.controller.AuthController')
fix_fxml('src/main/resources/aval/ui/views/Register.fxml', 'aval.ui.controller.RegisterController')

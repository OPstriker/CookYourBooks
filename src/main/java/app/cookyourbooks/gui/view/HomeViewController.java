package app.cookyourbooks.gui.view;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.NavigationService.View;

@SuppressWarnings("NullAway.Init")
public class HomeViewController {
  @FXML private Button startButton;
  private final NavigationService navigationService;

  public HomeViewController(NavigationService navigationService) {
    this.navigationService = navigationService;
  }

  @FXML
  @SuppressWarnings("UnusedMethod")
  private void initialize() {
    startButton.setOnAction(e -> navigationService.navigateTo(View.LIBRARY));
  }
}

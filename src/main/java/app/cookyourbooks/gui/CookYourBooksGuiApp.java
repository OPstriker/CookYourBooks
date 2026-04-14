package app.cookyourbooks.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;

import app.cookyourbooks.CybLibrary;
import app.cookyourbooks.adapters.GeminiOcrAdapter;
import app.cookyourbooks.adapters.gemini.RealGeminiClient;
import app.cookyourbooks.gui.view.HomeViewController;
import app.cookyourbooks.gui.view.ImportViewController;
import app.cookyourbooks.gui.view.LibraryViewController;
import app.cookyourbooks.gui.view.MainViewController;
import app.cookyourbooks.gui.view.RecipeEditorViewController;
import app.cookyourbooks.gui.view.SearchViewController;
import app.cookyourbooks.gui.view.ShoppingListResultViewController;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.gui.viewmodel.LibraryViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl;
import app.cookyourbooks.gui.viewmodel.ShoppingListResultViewModelImpl;
import app.cookyourbooks.gui.viewmodel.ShoppingListViewModelImpl;
import app.cookyourbooks.services.LibrarianServiceImpl;

/**
 * JavaFX entry point for CookYourBooks.
 *
 * <p>This class wires up the service layer, creates ViewModels, and launches the main window. It
 * demonstrates the dependency injection pattern you'll use to connect your feature ViewModels to
 * the service layer and navigation.
 *
 * <h2>Wiring pattern</h2>
 *
 * <ol>
 *   <li>Load the library (repositories + conversion registry)
 *   <li>Create service-layer objects
 *   <li>Create the shared {@link NavigationService}
 *   <li>Create your feature ViewModels, injecting services via constructors
 *   <li>Load each feature's FXML, injecting the ViewModel into the controller
 *   <li>Register each feature's view with the {@link MainViewController}
 * </ol>
 *
 * <h2>Adding your feature</h2>
 *
 * <p>Find the TODO comments below and follow the pattern to wire your ViewModel and View.
 */
public class CookYourBooksGuiApp extends Application {

  private static final Logger LOG = LoggerFactory.getLogger(CookYourBooksGuiApp.class);

  @Override
  public void start(Stage primaryStage) {
    // ── 1. Load the recipe library ──
    // CybLibrary reads cyb-library.json from the working directory and creates the in-memory
    // repositories (RecipeRepository, CollectionRepository) and the ConversionRegistry.
    CybLibrary library = CybLibrary.load(Path.of("cyb-library.json"));
    //

    // ── 2. Create services ──
    // LibrarianServiceImpl wraps the repositories and exposes high-level operations like
    // listCollections(), saveRecipe(), and searchByIngredient(). The same instance is shared
    // with all feature ViewModels so they all read/write the same data.
    var librarianService =
        new LibrarianServiceImpl(
            library.getRecipeRepository(), library.getCollectionRepository(), library);
    // var recipeService = new RecipeServiceImpl(
    //     library.getRecipeRepository(), library.getCollectionRepository(),
    //     library.getConversionRegistry());
    // Also available: TransformerServiceImpl, CookingServiceImpl, PlannerServiceImpl

    LOG.info("Loaded {} collections", librarianService.listCollections().size());

    // ── 3. Create shared navigation ──
    // NavigationService is shared by all ViewModels that need to navigate between views
    // (e.g. "open recipe X in the editor from the library view"). Import does not navigate
    // away, so the ImportViewModel does not hold a reference to navigationService.
    var navigationService = new NavigationService();

    // resultVm must be created before shoppingListVm so the onConfirm lambda can capture it.
    var resultVm = new ShoppingListResultViewModelImpl(librarianService, navigationService);

    // When the user confirms recipe selection, resolve the IDs to recipes, populate the result
    // screen, and navigate to it. The lambda runs on the FX thread (button click handler), so
    // BackgroundTaskRunner inside load() safely spawns the background fetch from there.
    var shoppingListVm =
        new ShoppingListViewModelImpl(
            selectedIds -> {
              resultVm.load(new HashSet<>(selectedIds));
              navigationService.navigateTo(NavigationService.View.SHOPPING_LIST);
            });

    // ── 4. Create the main layout ──
    // MainViewController manages the sidebar navigation and the content area that hosts
    // each feature's view. It is constructed here so that ViewModels can be registered
    // before the FXML is loaded.
    var mainController = new MainViewController(navigationService, shoppingListVm, resultVm);

    // ── 5. Wire your feature ViewModels and Views ──
    //
    // For each feature you implement, follow this pattern:
    //
    //   // Create your ViewModel (inject services via constructor)
    //   var libraryVm = new LibraryViewModelImpl(librarianService, navigationService);
    //
    //   // Load your FXML view (inject the ViewModel into the controller)
    //   FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    //   loader.setControllerFactory(clazz -> new LibraryViewController(libraryVm));
    //   Parent libraryView = loader.load();
    //
    //   // Register the view with the main controller
    //   mainController.setViewNode(NavigationService.View.LIBRARY, libraryView);

    // -- Wire Home View --
    try {
      FXMLLoader homeLoader = new FXMLLoader(getClass().getResource("/fxml/HomeView.fxml"));
      homeLoader.setControllerFactory(clazz -> new HomeViewController(navigationService));
      Parent homeView = homeLoader.load();
      mainController.setViewNode(NavigationService.View.HOME, homeView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load HomeView.fxml", e);
    }

    // ── Wire Library View ──
    // libraryVm is hoisted outside the try block so it can be referenced later in the
    // Library auto-refresh listener (added below after all views are wired). If the FXML
    // fails to load, libraryVm remains null and the refresh listener is skipped safely.
    LibraryViewModelImpl libraryVm = null;
    try {
      libraryVm =
          new LibraryViewModelImpl(librarianService, navigationService, Duration.ofSeconds(5));
      FXMLLoader libraryLoader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
      final LibraryViewModelImpl finalLibraryVmForLoader = libraryVm;
      libraryLoader.setControllerFactory(
          clazz -> new LibraryViewController(finalLibraryVmForLoader, shoppingListVm));
      Parent libraryView = libraryLoader.load();
      mainController.setViewNode(NavigationService.View.LIBRARY, libraryView);
    } catch (IOException e) {
      LOG.error("Failed to load LibraryView.fxml", e);
    }

    // ── Wire Recipe Editor ──
    wireRecipeEditor(mainController, navigationService, library);

    // ── Wire Import Interface ──
    wireImportInterface(mainController, navigationService, library, librarianService);

    // ── Wire Shopping List Result ──
    wireShoppingListResult(mainController, resultVm);

    // TODO: Wire Search & Filter (teams of 4 only)
    // TODO: Wire Import Interface

    // ── Search & Filter ──────────────────────────────────────────────────────
    var searchVm =
        new SearchViewModelImpl(librarianService, navigationService, Duration.ofMillis(300));
    try {
      FXMLLoader searchLoader = new FXMLLoader(getClass().getResource("/fxml/SearchView.fxml"));
      // setController() is used here (not setControllerFactory()) because SearchView.fxml has
      // no fx:controller attribute — the factory would never be called and initialize() would
      // never run, leaving all @FXML fields null. See the MainView comment above for details.
      searchLoader.setController(new SearchViewController(searchVm));
      Parent searchView = searchLoader.load();
      mainController.setViewNode(NavigationService.View.SEARCH, searchView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load SearchView.fxml", e);
    }

    // ── Search auto-fetch on navigation ──
    //
    // SearchViewController.initialize() only runs once at startup. Without this listener,
    // navigating away and back to Search leaves the list stale (or empty on first click).
    // Calling clearFilters() on every SEARCH navigation resets the query and fires
    // listAllRecipes(), so the user always sees a fresh full recipe list when they open Search.
    navigationService
        .currentViewProperty()
        .addListener(
            (obs, oldView, newView) -> {
              if (newView == NavigationService.View.SEARCH) {
                searchVm.clearFilters();
              }
            });

    // ── Library auto-refresh on navigation ──
    //
    // After a recipe is imported the Library view does not update automatically because the
    // library ViewModel only loads data once (at construction). Adding a listener on
    // currentViewProperty() triggers libraryVm.refresh() every time the user navigates to
    // the LIBRARY view — so imported recipes appear immediately without restarting the app.
    //
    // A final local variable is required here because Java lambda capture rules require
    // variables referenced inside a lambda to be effectively final. We captured libraryVm
    // outside the try block precisely so we could do this.
    final LibraryViewModelImpl finalLibraryVm = libraryVm;
    if (finalLibraryVm != null) {
      navigationService
          .currentViewProperty()
          .addListener(
              (obs, oldView, newView) -> {
                if (newView == NavigationService.View.LIBRARY) {
                  // refresh() runs a background load via BackgroundTaskRunner so the FX thread
                  // is never blocked — safe to call from this property listener.
                  finalLibraryVm.refresh();
                }
              });
    }

    // ── 6. Load the main layout and show the window ──
    try {
      FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));

      // IMPORTANT: Use setController() here, NOT setControllerFactory().
      //
      // setControllerFactory() is only invoked by FXMLLoader when the FXML file has an
      // fx:controller attribute. MainView.fxml intentionally does NOT have fx:controller
      // (to allow the controller to be supplied externally), so the factory would never be
      // called and MainViewController.initialize() would never run — leaving the sidebar
      // buttons with no action handlers (the "click Import → nothing happens" bug).
      //
      // setController() directly provides the pre-constructed controller object and is
      // called regardless of whether fx:controller is present in the FXML. This is the
      // correct method to use here.
      mainLoader.setController(mainController);
      Parent root = mainLoader.load();

      Scene scene = new Scene(root, 960, 640);
      primaryStage.setTitle("CookYourBooks");
      primaryStage.setScene(scene);
      primaryStage.show();
    } catch (IOException e) {
      throw new RuntimeException("Failed to load MainView.fxml", e);
    }
  }

  /**
   * Wires the Shopping List result screen: loads the FXML, injects the ViewModel into the
   * controller, and registers the view with the main controller.
   *
   * <p>Extracted from {@link #start} to keep that method under the 150-line Checkstyle limit.
   *
   * @param mainController the main layout controller to register the view with
   * @param resultVm the Shopping List result ViewModel (already constructed in {@link #start})
   */
  private void wireShoppingListResult(
      MainViewController mainController, ShoppingListResultViewModelImpl resultVm) {
    try {
      FXMLLoader loader =
          new FXMLLoader(getClass().getResource("/fxml/ShoppingListResultView.fxml"));
      // setController() — not setControllerFactory() — because ShoppingListResultView.fxml
      // has no fx:controller attribute (same reason as SearchView.fxml and MainView.fxml).
      loader.setController(new ShoppingListResultViewController(resultVm));
      Parent view = loader.load();
      mainController.setViewNode(NavigationService.View.SHOPPING_LIST, view);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ShoppingListResultView.fxml", e);
    }
  }

  /**
   * Wires the main Recipe Editor: creates its ViewModel, loads the FXML, registers a navigation
   * listener so clicking a recipe in the Library opens it in the editor, and registers the view
   * with the main controller.
   *
   * <p>Extracted from {@link #start} to keep that method under the 150-line Checkstyle limit.
   */
  private void wireRecipeEditor(
      MainViewController mainController, NavigationService navigationService, CybLibrary library) {
    // ViewModel: only needs RecipeRepository (no librarianService — not saving to collections).
    var recipeEditorVm = new RecipeEditorViewModelImpl(library.getRecipeRepository());
    FXMLLoader recipeLoader = new FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
    recipeLoader.setControllerFactory(
        clazz -> new RecipeEditorViewController(recipeEditorVm, navigationService));

    // When the user selects a recipe (e.g. from Library), load it into the editor.
    navigationService
        .selectedRecipeIdProperty()
        .addListener(
            (obs, oldId, newId) -> {
              if (newId != null) {
                recipeEditorVm.statusMessageProperty().set("");
                recipeEditorVm.loadRecipe(newId);
              }
            });
    try {
      Parent recipeEditorView = recipeLoader.load();
      mainController.setViewNode(NavigationService.View.RECIPE_EDITOR, recipeEditorView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load RecipeEditorView.fxml", e);
    }
  }

  /**
   * Wires the Import Interface: creates the Import ViewModel, loads a dedicated embedded Recipe
   * Editor (separate FXML instance from the main editor), and registers both views.
   *
   * <p>The embedded editor is pre-populated via {@code loadDraft()} when OCR completes. When the
   * embedded editor saves successfully, {@code ImportViewController} calls {@code acceptImport()}
   * to return the Import VM to IDLE.
   *
   * <p>Extracted from {@link #start} to keep that method under the 150-line Checkstyle limit.
   *
   * @param librarianService needed by importEditorVm so saveDraft() can assign the recipe to a
   *     collection (recipeRepository.save() alone would not add it to any collection)
   */
  private void wireImportInterface(
      MainViewController mainController,
      NavigationService navigationService,
      CybLibrary library,
      LibrarianServiceImpl librarianService) {
    // Load the API key from .env (falls back to the real environment variable if .env is absent).
    // dotenv-java reads .env from the current working directory; ignoreIfMissing() prevents a
    // crash when running in CI where there is no .env file.
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    String apiKey = dotenv.get("GOOGLE_API_KEY");
    var ocrService = new GeminiOcrAdapter(new RealGeminiClient(apiKey));
    var importVm = new ImportViewModelImpl(ocrService, librarianService);

    // Separate RecipeEditorViewModelImpl instance for import-draft use: needs librarianService
    // so saveDraft() calls saveRecipe(recipe, collectionId) — persists AND assigns collection.
    var importEditorVm =
        new RecipeEditorViewModelImpl(library.getRecipeRepository(), librarianService);
    try {
      // Second FXML load of RecipeEditorView — creates a fresh controller + view instance
      // independent of the main Recipe Editor registered under RECIPE_EDITOR.
      FXMLLoader importEditorLoader =
          new FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
      importEditorLoader.setControllerFactory(
          clazz -> new RecipeEditorViewController(importEditorVm, navigationService));
      Parent importEditorView = importEditorLoader.load();

      FXMLLoader importLoader = new FXMLLoader(getClass().getResource("/fxml/ImportView.fxml"));
      importLoader.setControllerFactory(
          clazz ->
              new ImportViewController(
                  importVm, importEditorVm, importEditorView, navigationService));
      Parent importView = importLoader.load();
      mainController.setViewNode(NavigationService.View.IMPORT, importView);

      // Re-load the collection list every time the user navigates to the Import view so that
      // any collections added (or renamed/deleted) in the Library view are immediately visible
      // in the collection ComboBox — same pattern as the Library auto-refresh.
      navigationService
          .currentViewProperty()
          .addListener(
              (obs, oldView, newView) -> {
                if (newView == NavigationService.View.IMPORT) {
                  importVm.loadCollections();
                }
              });
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ImportView.fxml or RecipeEditorView.fxml", e);
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}

package com.github.sergueik.swet;

/**
 * Copyright 2014 - 2017 Serguei Kouzmine
 */

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.apache.log4j.Category;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import org.mihalis.opal.breadcrumb.Breadcrumb;
import org.mihalis.opal.breadcrumb.BreadcrumbItem;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.passer.ChoiceItem;
import org.passer.ChoicesDialog;

import com.github.sergueik.swet.ExceptionDialogEx;
import com.github.sergueik.swet.Utils;

/**
 * Main form for Selenium WebDriver Elementor Tool (SWET)
 * @author: Serguei Kouzmine (kouzmine_serguei@yahoo.com)
 */

public class SimpleToolBarEx {

	private Shell shell;
	private WebDriver driver;
	private WebDriverWait wait;
	private int flexibleWait = 5;
	private int implicitWait = 1;
	private long pollingInterval = 500;
	private String baseURL = "about:blank";
	// private final String getCommand = "return document.swdpr_command ===
	// undefined ? '' : document.swdpr_command;";
	private List<String> stepKeys = new ArrayList<>();
	private Map<String, Map<String, String>> testData = new HashMap<>();
	private Map<String, Image> iconCache = new HashMap<>();
	private static final int IMAGE_SIZE = 32;
	private Configuration config = null;
	private SimpleToolBarEx app;
	@SuppressWarnings("deprecation")
	static final Category logger = Category.getInstance(SimpleToolBarEx.class);
	private static StringBuilder loggingSb = new StringBuilder();
	private static Formatter formatter = new Formatter(loggingSb, Locale.US);

	private static Map<String, Boolean> browserStatus = new HashMap<>();
	private static String configFilePath; // TODO: rename
	static Map<String, String> browserDefaults = new HashMap<>();
	static {
		browserDefaults.put("windows", "Chrome");
		browserDefaults.put("linux", "Firefox");
		browserDefaults.put("mac", "Safari");
	}
	// "Chrome" or better
	static String browserDefault = Utils.getPropertyEnv("browser.default",
			browserDefaults.get(Pattern.compile(" +").split(OSUtils.getOsName())[0]));
	private static Map<String, String> configData = new HashMap<>();
	static {
		configData.put("Browser", browserDefault);
		configData.put("Template", "Core Selenium Java (embedded)");
	}
	private static final String defaultConfig = String.format(
			"{ \"Browser\": \"%s\", \"Template\": \"%s\", }",
			configData.get("Browser"), configData.get("Template"));

	private static final int shellWidth = 768;
	private static final int shellHeight = 324;
	private static int step_index = 0;
	private static String osName = OSUtils.getOsName();
	private static Display display;
	private String generatedScript = null;
	private Label statusMessage;
	private TemplateCache templateCache = TemplateCache.getInstance();
	private Utils utils = Utils.getInstance();
	private ToolItem launchTool = null;
	private ToolItem saveTool = null;
	private ToolItem pageExploreTool = null;
	private static String defaultTemplateResourcePath = "templates/core_selenium_java.twig";

	private Breadcrumb bc;

	@SuppressWarnings("unused")
	public SimpleToolBarEx() {
		app = this;
		utils.initializeLogger();
		logger.info("Initialized logger.");
	}

	public void open(Display display) {
		testData = new HashMap<>(); // infer
		shell = new Shell(display, SWT.CENTER | SWT.SHELL_TRIM); // (~SWT.RESIZE)));
		Rectangle boundRect = new Rectangle(0, 0, shellWidth, shellHeight);
		shell.setBounds(boundRect);
		shell.setImage(new Image(display,
				utils.getResourceStream("images/document_wrench_color.ico")));
		try {

			iconCache.put("launch icon",
					resize(getImage(String.format("images/%s", launchImage)), IMAGE_SIZE,
							IMAGE_SIZE));
			iconCache.put("find icon",
					getImage(String.format("images/%s", findImage)));
			iconCache.put("prefs icon",
					getImage(String.format("images/%s", gearImage)));
			iconCache.put("shutdown icon",
					getImage(String.format("images/%s", quitImage)));
			iconCache.put("step icon",
					getImage(String.format("images/%s", "document_wrench_bw.png")));
			iconCache.put("codeGen icon",
					resize(getImage(String.format("images/%s", codeGenImage)), 32, 32));
			iconCache.put("open icon",
					getImage(String.format("images/%s", openImage)));
			iconCache.put("save icon",
					getImage(String.format("images/%s", saveImage)));
			iconCache.put("help icon",
					getImage(String.format("images/%s", helpImage)));
			iconCache.put("testsuite icon",
					getImage(String.format("images/%s", testsuiteImage)));
		} catch (Exception e) {
			logger.error("Cannot load images: " + e.getMessage());
			System.exit(1);
		}

		shell.setText(String.format("Selenium Webdriver Elementor Toolkit"));
		GridLayout gl = new GridLayout();
		gl.numColumns = 1;
		shell.setLayout(gl);

		ToolBar toolBar = new ToolBar(shell, SWT.BORDER | SWT.HORIZONTAL);

		launchTool = new ToolItem(toolBar, SWT.PUSH);
		launchTool.setImage(iconCache.get("launch icon"));
		launchTool.setToolTipText("Launch browser");

		pageExploreTool = new ToolItem(toolBar, SWT.PUSH);
		pageExploreTool.setImage(iconCache.get("find icon"));
		// TODO: setDisabledImage
		pageExploreTool.setToolTipText("Inject script");

		ToolItem codeGenTool = new ToolItem(toolBar, SWT.PUSH);
		codeGenTool.setImage(iconCache.get("codeGen icon"));
		codeGenTool.setToolTipText("Generate program");

		ToolItem testsuiteTool = new ToolItem(toolBar, SWT.PUSH);
		testsuiteTool.setImage(iconCache.get("testsuite icon"));
		testsuiteTool.setToolTipText("Generate Excel TestSuite");

		new ToolItem(toolBar, SWT.SEPARATOR);

		ToolItem openTool = new ToolItem(toolBar, SWT.PUSH);
		openTool.setImage(iconCache.get("open icon"));
		openTool.setToolTipText("Load session");

		saveTool = new ToolItem(toolBar, SWT.PUSH);
		saveTool.setImage(iconCache.get("save icon"));
		saveTool.setToolTipText("Save session");

		ToolItem preferencesTool = new ToolItem(toolBar, SWT.PUSH);
		preferencesTool.setImage(iconCache.get("prefs icon"));
		preferencesTool.setToolTipText("Configure");

		ToolItem helpTool = new ToolItem(toolBar, SWT.PUSH);
		helpTool.setImage(iconCache.get("help icon"));
		helpTool.setToolTipText("help using the tool");

		ToolItem shutdownTool = new ToolItem(toolBar, SWT.PUSH);
		shutdownTool.setImage(iconCache.get("shutdown icon"));
		shutdownTool.setToolTipText("Quit");

		pageExploreTool.setEnabled(false);
		testsuiteTool.setEnabled(false);
		codeGenTool.setEnabled(false);
		saveTool.setEnabled(false);

		toolBar.pack();

		Composite composite = new Composite(shell, SWT.BORDER);

		GridLayout gridLayout = new GridLayout();
		gridLayout.marginLeft = 5;
		gridLayout.marginTop = 5;
		gridLayout.marginRight = 5;
		gridLayout.marginBottom = 5;
		gridLayout.numColumns = 1;
		gridLayout.makeColumnsEqualWidth = false;
		composite.setLayout(gridLayout);

		Breadcrumb bc1 = new Breadcrumb(composite, SWT.BORDER);
		bc = bc1;
		composite.pack();

		this.statusMessage = new Label(shell, SWT.NONE);
		statusMessage.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		/*
		final GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.widthHint = statusMessage.getParent().getBounds().width;
		gd.heightHint = 28; 
		statusMessage.setLayoutData(gd);
		*/
		updateStatus("Loading");

		testsuiteTool.addListener(SWT.Selection, event -> {
			testsuiteTool.setEnabled(false);
			updateStatus("Launching the TestSuite Excel exporter");
			try {
				TableEditorEx tableEditor = new TableEditorEx(Display.getCurrent(),
						shell);
				for (String key : testData.keySet()) {
					String jsonData = utils.writeDataJSON(testData.get(key), "{}");
					logger
							.info(String.format("Sending the key %s", key /*, jsonData */ ));
					tableEditor.setData(key, jsonData);
				}
				tableEditor.render();
			} catch (Exception e) {
				// show the error dialog with exception trace
				ExceptionDialogEx.getInstance().render(e);
			}
			testsuiteTool.setEnabled(true);
			updateStatus("Ready");
		});

		launchTool.addListener(SWT.Selection, event -> {
			launchTool.setEnabled(false);
			String browser = configData.get("Browser");
			logger.info(String.format("Launching the %s browser", browser));
			updateStatus(String.format("Launching the %s browser", browser));
			if (configData.containsKey("Base URL")) {
				baseURL = configData.get("Base URL");
			}
			if (initializeBrowser(browser, baseURL)) {
				// prevent the customer from launching multiple instances
				// launchTool.setEnabled(true);
				pageExploreTool.setEnabled(true);
				codeGenTool.setEnabled(true);
				testsuiteTool.setEnabled(true);
				// driver.get(getResourceURI("blankpage.html"));
			} else {
				// allow the user to try again
				launchTool.setEnabled(true);
			}
			// TODO: detect if closed ?
			updateStatus("Ready");
		});

		codeGenTool.addListener(SWT.Selection, event -> {
			codeGenTool.setEnabled(false);
			testsuiteTool.setEnabled(false);
			RenderTemplate renderTemplate = new RenderTemplate();
			if (configData.containsKey("Template Path")) {
				updateStatus(String.format("Reading template path \"%s\" \u2026",
						configData.get("Template Path")));
				renderTemplate.setTemplateAbsolutePath(configData.get("Template Path")
						.replace("\\\\", "\\").replace("\\", "/"));
			} else {

				updateStatus(String.format("Finding template: %s \u2026",
						configData.get("Template")));
				templateCache.fillEmbeddedTemplateCache();
				String templateResourcePath = templateCache
						.getItem(templateCache.approxLookup(configData.get("Template")));
				if (templateResourcePath == null) {
					logger.info(
							"Using the default template: " + defaultTemplateResourcePath);
					renderTemplate.setTemplateName(defaultTemplateResourcePath);
				} else {
					renderTemplate.setTemplateName(templateResourcePath);
				}
			}
			generatedScript = "";
			try {
				generatedScript = renderTemplate.renderTest(testData);
			} catch (Exception e) {
				// show the error dialog with exception trace
				ExceptionDialogEx.getInstance().render(e);
			}
			shell.setData("payload", generatedScript);
			ScrolledTextEx test = new ScrolledTextEx(Display.getCurrent(), shell);
			updateStatus("Ready");
			codeGenTool.setEnabled(true);
			testsuiteTool.setEnabled(true);
		});

		openTool.addListener(SWT.Selection, event -> {
			openTool.setEnabled(false);
			FileDialog dialog = new FileDialog(shell, SWT.OPEN);
			String[] filterNames = new String[] { "YAML sources", "All Files (*)" };
			String[] filterExtensions = new String[] { "*.yaml", "*" };
			dialog.setFilterNames(filterNames);
			dialog.setFilterExtensions(filterExtensions);
			configFilePath = dialog.open();
			if (configFilePath != null) {
				logger.info("Loading " + configFilePath);
				config = YamlHelper.loadConfiguration(configFilePath);
				testData = config.getElements();

				List<String> sortedSteps = utils.sortSteps(testData, "CommandId",
						"ElementStepNumber");

				for (String stepId : sortedSteps) {

					Map<String, String> elementData = testData.get(stepId);

					// Append Breadcrump Button

					String commandId = elementData.get("CommandId");
					stepKeys.add(commandId);
					addBreadCrumpItem(elementData.get("ElementCodeName"), commandId,
							elementData, bc);
					shell.layout(true, true);
					shell.pack();
				}
				// YamlHelper.printConfiguration(config);
				shell.layout(true, true);
				shell.pack();
				saveTool.setEnabled(true);
			}
			openTool.setEnabled(true);
			codeGenTool.setEnabled(true);
			testsuiteTool.setEnabled(true);
			updateStatus("Ready");
		});

		saveTool.addListener(SWT.Selection, event -> {
			saveTool.setEnabled(false);
			saveWorkspace(shell);
			saveTool.setEnabled(true);
		});

		preferencesTool.addListener(SWT.Selection, event -> {
			preferencesTool.setEnabled(false);
			shell.setData("updated", false);

			shell.setData("CurrentConfig",
					utils.writeDataJSON(configData, defaultConfig));
			ConfigFormEx o = new ConfigFormEx(Display.getCurrent(), shell);
			o.render();
			if ((Boolean) shell.getData("updated")) {
				utils.readData((String) shell.getData("CurrentConfig"),
						Optional.of(configData));
			}
			preferencesTool.setEnabled(true);
		});

		pageExploreTool.setData("Application", app);
		// see also:
		// https://eclipsesource.com/blogs/2014/03/24/how-to-use-swt-with-java-8/
		pageExploreTool
				.addSelectionListener(new AsyncDataCollectionListener(pageExploreTool));

		shutdownTool.addListener(SWT.Selection, event -> {
			shutdownTool.setEnabled(false);

			/*
			 *  prompt the user confirmation dialog
			 */

			ChoiceItem[] items = new ChoiceItem[] {
					new ChoiceItem("Exit and save my project",
							"Save your work in progress and exit the program"),
					new ChoiceItem("Exit and don't save",
							"Exit the program without saving your project"),
					new ChoiceItem("Close browser and continue",
							"Close the browser and continue"),
					new ChoiceItem("Don't exit", "Return to the program") };

			ChoicesDialog dialog = new ChoicesDialog(shell, SWT.APPLICATION_MODAL);

			updateStatus("Shutting down");
			dialog.setTitle("Exit");
			dialog.setMessage("Do you really want to exit?");
			dialog.setImage(Display.getCurrent().getSystemImage(SWT.ICON_QUESTION));
			dialog.setChoices(items);
			dialog.setDefaultChoice(items[3]);
			dialog.setShowArrows(false);

			int choice = dialog.open();
			logger.info("Selected: " + items[choice].getText());
			switch (choice) {
			case -1: /* dialog closed */
				updateStatus("Ready");
				shutdownTool.setEnabled(true);
				break;
			case 0: /* Save the session and exit */
				saveWorkspace(shell);
				closeBrowser();
				shell.getDisplay().dispose();
				System.exit(0);
				break;
			case 1: /* exit without saving */
				closeBrowser();
				shell.getDisplay().dispose();
				System.exit(0);
				break;
			case 2: /* close the browser */
				closeBrowser();
				updateStatus("Ready");
				shutdownTool.setEnabled(true);
				break;
			case 3: /* return to the program */
				updateStatus("Ready");
				shutdownTool.setEnabled(true);
				break;
			}
		});

		updateStatus("Ready");
		shell.setText("Selenium WebDriver Elementor Toolkit");
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	private void closeBrowser() {
		if (driver != null) {
			try {
				BrowserDriver.close();
				driver = null;
			} catch (Exception e) {
				logger.debug("Exception (ignored): " + e.toString());
			} finally {
				launchTool.setEnabled(true);
			}
		}
	}

	private void saveWorkspace(Shell shell) {
		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setFilterNames(new String[] { "YAML Files", "All Files (*.*)" });
		dialog.setFilterExtensions(new String[] { "*.yaml", "*.*" });
		String homeDir = System.getProperty("user.home");
		dialog.setFilterPath(homeDir); // Windows path
		String path = null;
		if (configFilePath != null) {
			dialog.setFileName(configFilePath);
			path = new String(configFilePath);
		} //
		configFilePath = dialog.open();
		if (configFilePath != null) {
			System.out.println("Save to: " + configFilePath);
			if (config == null) {
				config = new Configuration();
			}
			logger.info("Saving unordered test data");
			config.setElements(testData);
			// Save unordered, order by step index when generating script, drawing
			// buttons etc.
			logger.info("Save configuration YAML in " + configFilePath);
			YamlHelper.saveConfiguration(config, configFilePath);
		} else {
			logger.warn("Save dialog returns no path info.");
			if (path != null) {
				configFilePath = new String(path);
			}
		}
	}

	// TODO: detect closed browser ?
	public Thread detectBrowserClosed(WebDriver driver,
			Map<String, Boolean> browserStatus) {
		return new Thread() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					try {
						driver.getCurrentUrl();
					} catch (Exception e) {
						logger.warn("Should be signaling that browser is closed");
						browserStatus.replace("closed", true);
						pageExploreTool.getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								app.pageExploreTool.setEnabled(true);
								app.saveTool.setEnabled(true);
							}
						});

						break;
					}
				}
			}
		};
	}

	// http://www.vogella.com/tutorials/EclipseJobs/article.html#using-syncexec-and-asyncexec
	public Thread detectPageChange(WebDriver driver,
			Map<String, Boolean> browserStatus) {
		final String URL = driver.getCurrentUrl();

		return new Thread() {
			public void run() {

				while (true) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					try {
						if (driver.getCurrentUrl().indexOf(URL) != 0) {
							logger.info("Signaling URL change.");
							browserStatus.replace("runaway", true);

							pageExploreTool.getDisplay().asyncExec(new Runnable() {
								@Override
								public void run() {
									pageExploreTool.setEnabled(true);
									app.saveTool.setEnabled(true);
								}
							});

							break;
						}
					} catch (NoSuchSessionException e) {
						// possibly closing the application
					} catch (WebDriverException e) {
						// possibly closing the application
					}
				}
			}
		};
	}

	private Map<String, String> addElementLocatorInformation() {

		Map<String, String> elementData = new HashMap<>();
		Boolean waitingForData = true;
		Boolean browserRunaway = false;
		String name = null;
		while (waitingForData) {
			String payload = utils.getpayload();
			if (!payload.isEmpty()) {
				if (payload.contains((CharSequence) "ElementCodeName")) {
					// objects cannot suicide
					elementData = new HashMap<>();
					name = utils.readVisualSearchResult(payload,
							Optional.of(elementData));
					logger.debug("Processing payload...");
					if (name == null || name.isEmpty()) {
						logger.info("Rejected unfinished visual search.");
					} else {
						logger.info(
								String.format("Received element data for the element: '%s'\n%s",
										name, payload));
						elementData.put("ElementPageURL", utils.getCurrentUrl());
						waitingForData = false;
						break;
					}
				}
			}
			if (browserStatus.get("runaway")) {
				logger.info("Detected URL change");
				browserRunaway = true;
				waitingForData = false;
			}
			if (waitingForData) {
				try {
					// TODO: add the alternative code to
					// bail if waited long enough already
					logger.info("Waiting.");
					Thread.sleep(1000);
				} catch (InterruptedException exception) {
				}
			}
		}
		// clear results on the page
		utils.flushVisualSearchResult();
		if (!browserRunaway) {
			utils.closeVisualSearch();
		}
		return elementData;
	}

	private boolean initializeBrowser(String browser, String baseURL) {
		try {
			driver = BrowserDriver.initialize(browser);
			utils.setDriver(driver);
			utils.setFlexibleWait(flexibleWait);
			driver.manage().timeouts().pageLoadTimeout(50, TimeUnit.SECONDS)
					.implicitlyWait(implicitWait, TimeUnit.SECONDS)
					.setScriptTimeout(30, TimeUnit.SECONDS);
			driver.get(baseURL);
			// prevent the customer from launching multiple instances
			// launchTool.setEnabled(true);
			if (!osName.startsWith("mac")) {
				// TODO: add a sorry dialog for Mac / Safari, any OS / Firefox
				// combinations
			}
			return true;
		} catch (Exception e) {
			ExceptionDialogEx.getInstance().render(e);
			return false;
		}
	}

	// Paginates the BreadCrump
	private void paginateBreadCrump() {
		Rectangle rect = bc.getBounds();
		if (rect.width > shell.getBounds().width - 5
				|| rect.width > java.awt.Toolkit.getDefaultToolkit()
						.getScreenSize().width - 100) {
			Breadcrumb bc2 = new Breadcrumb(bc.getParent(), SWT.BORDER);
			// NOTE: operates global
			bc = bc2;
		}
	}

	// Adds a bredCrump item to BreadCrump canvas
	// attached Shell / Form for Element editing
	private void addBreadCrumpItem(String name, String commandId,
			Map<String, String> data, Breadcrumb bc) {
		paginateBreadCrump();
		final BreadcrumbItem item = new BreadcrumbItem(bc, SWT.CENTER | SWT.TOGGLE);
		item.setData("CommandId", commandId);
		int step_number = (data.containsKey("ElementStepNumber"))
				? Integer.parseInt(data.get("ElementStepNumber")) : step_index;
		item.setText(String.format("Step %d: %s", step_number + 1, name));
		item.setImage(iconCache.get("step icon"));
		item.setSelectionImage(iconCache.get("step icon"));

		// NOTE: MouseDown event is not received
		item.addListener(SWT.MouseDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				logger.info("MouseDown Button: " + event.button);
				if (event.button == 3) {
				}
			}
		});

		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				String commandId = e.item.getData("CommandId").toString();
				assertThat(stepKeys, hasItem(commandId));
				assertTrue(testData.containsKey(commandId));
				Map<String, String> elementData = testData.get(commandId);
				logger
						.info(String.format("Clicked %s / %s", item.getText(), commandId));
				shell.setData("CurrentCommandId", commandId);
				shell.setData("updated", false);
				// spawn a separate shell for editing the element attributes
				ComplexFormEx cs = new ComplexFormEx(Display.getCurrent(), shell);
				for (String key : elementData.keySet()) {
					logger.info(
							"Passing to the dialog: " + key + ": " + elementData.get(key));
					cs.setData(key, elementData.get(key));
				}
				cs.render();
				if ((Boolean) shell.getData("updated")) {
					Map<String, String> data = new HashMap<>();
					// form sets result to the modified element attributes JSON
					String name = utils.readData((String) shell.getData("result"),
							Optional.of(data));
					if (name != null) {
						testData.replace(commandId, data);
						// or an empty JSON when element is deleted,
					} else {
						// Clear test data
						testData.remove(commandId);
						// Remove the current item
						Breadcrumb b = item.getParent();
						b.removeItem(item);
						b.pack();
					}
				}
			}
		});
		step_index++;
	}

	@Override
	public void finalize() {
		disposeIconCache();
	}

	private void updateStatus(String newStatus) {
		// NOTE: there is no `HORIZONTAL ELLIPSIS` in code page 437
		logger.info(String.format("%s%s", newStatus,
				osName.startsWith("windows") ? "..." : "\u2026"));
		this.statusMessage.setText(String.format("%s\u2026", newStatus));
		this.statusMessage.pack();
		this.shell.pack();

	}

	public static void main(String[] args) {
		display = new Display();

		SimpleToolBarEx simpleToolBarEx = new SimpleToolBarEx();
		simpleToolBarEx.setCodeGenImage("code_128.png");

		simpleToolBarEx.open(display);
		simpleToolBarEx.finalize();
		display.dispose();
	}

	private String launchImage = "browsers_32.png";

	public void setLaunchImage(final String data) {
		this.launchImage = data;
	}

	private String helpImage = "help_32.png";

	public void sethelpImage(final String data) {
		this.helpImage = data;
	}

	private String findImage = "find_32.png";

	public void setFindImage(final String data) {
		this.findImage = data;
	}

	private String gearImage = "preferences_32.png";

	public void setGearImage(final String data) {
		this.gearImage = data;
	}

	// TODO: cleanup
	private String pageImage = "page_36.png";

	public void setPageImage(final String data) {
		this.pageImage = data;
	}

	private String openImage = "open_32.png";

	public void setOpenImage(final String data) {
		this.openImage = data;
	}

	private String saveImage = "save_32.png";

	public void setSaveImage(final String data) {
		this.saveImage = data;
	}

	private String quitImage = "quit_32.png";

	public void setQuitImage(final String data) {
		this.quitImage = data;
	}

	private String testsuiteImage = "excel_gen_32.png";

	public void settestsuiteImage(final String data) {
		this.testsuiteImage = data;
	}

	private String codeGenImage = "codegen_32.png";

	public void setCodeGenImage(final String data) {
		this.codeGenImage = data;
	}

	// http://aniszczyk.org/2007/08/09/resizing-images-using-swt/
	private Image resize(Image image, int width, int height) {
		Image scaled = new Image(display, width, height);
		GC gc = new GC(scaled);
		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);
		gc.drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height,
				0, 0, width, height);
		gc.dispose();
		image.dispose();
		return scaled;
	}

	// https://github.com/vogellacompany/codeexamples-java/blob/master/de.vogella.databinding.windowbuilder.example/src/com/swtdesigner/SWTResourceManager.java

	private static Image getMissingImage() {
		Image image = new Image(Display.getCurrent(), IMAGE_SIZE, IMAGE_SIZE);
		GC gc = new GC(image);
		gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
		gc.fillRectangle(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		gc.dispose();
		return image;
	}

	protected static Image getImage(InputStream stream) throws IOException {
		try {
			Display display = Display.getCurrent();
			ImageData data = new ImageData(stream);
			if (data.transparentPixel > 0) {
				return new Image(display, data, data.getTransparencyMask());
			}
			return new Image(display, data);
		} finally {
			stream.close();
		}
	}

	public Image getImage(String imageResourcePath) {
		Image image = null;
		// image = imageCache.get(imageResourcePath);
		if (image == null) {
			try {
				image = getImage(utils.getResourceStream(imageResourcePath));
			} catch (Exception e) {
				image = getMissingImage();
			}
			// imageCache.put(imageResourcePath, image);
		}
		return image;
	}

	// dispose loaded images
	public void disposeIconCache() {
		for (Image image : iconCache.values()) {
			image.dispose();
		}
		iconCache.clear();
	}

	// see also:
	// http://www.gnu.org/software/kawa/api/index.html?gnu/jemacs/swt/SwtHelper.html
	private class AsyncDataCollectionListener implements SelectionListener {
		private ToolItem parentToolItem;
		private SimpleToolBarEx parentApp;

		AsyncDataCollectionListener(ToolItem parentToolItem) {
			this.parentToolItem = parentToolItem;
			parentApp = (SimpleToolBarEx) parentToolItem.getData("Application");
		}

		public void widgetDefaultSelected(SelectionEvent e) {
		}

		public void widgetSelected(SelectionEvent e) {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					SimpleToolBarEx.browserStatus.put("runaway", false);
					parentApp.detectPageChange(driver, browserStatus).start();
					parentToolItem.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							parentToolItem.setEnabled(false);
							parentApp.updateStatus("Inject script");
						}
					});
					wait = new WebDriverWait(parentApp.driver, flexibleWait);
					wait.pollingEvery(pollingInterval, TimeUnit.MILLISECONDS);
					try {
						utils.injectElementSearch(Optional.<String> empty());
					} catch (Exception e) {
						// show the error dialog with exception trace
						parentToolItem.getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								ExceptionDialogEx.getInstance().render(e);
							}
						});
					}

					parentToolItem.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							parentApp.updateStatus("Waiting for data");
						}
					});
					Map<String, String> elementData = parentApp
							.addElementLocatorInformation();
					if (!elementData.containsKey("CommandId")) {
						// TODO: better handle invalid elementData
					} else {
						// TODO: add radios to the ElementSearch Form
						if (!elementData.containsKey("ElementSelectedBy")) {
							elementData.put("ElementSelectedBy", "ElementCssSelector");
						}
						logger.info(
								"ElementSelectedBy : " + elementData.get("ElementSelectedBy"));
						// Append a Breadcrumb Item Button
						String commandId = elementData.get("CommandId");
						elementData.put("ElementStepNumber",
								String.format("%d", step_index));

						parentApp.testData.put(commandId, elementData);
						parentApp.stepKeys.add(commandId);
						parentToolItem.getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								parentApp.addBreadCrumpItem(elementData.get("ElementCodeName"),
										commandId, elementData, bc);
								parentApp.shell.layout(true, true);
								parentApp.shell.pack();
								parentToolItem.setEnabled(true);
								parentApp.updateStatus("Ready");
								browserStatus.put("runaway", false);
								parentApp.saveTool.setEnabled(true);
							}
						});

					}
				}
			});
			thread.start();
		}
	}
}
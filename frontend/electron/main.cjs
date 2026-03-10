const { app, BrowserWindow, dialog } = require("electron");
const path = require("path");
const fs = require("fs");
const http = require("http");
const { spawn } = require("child_process");

let mainWindow = null;
let backendProcess = null;

function resolveJavaCommand() {
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    const javaExe =
      process.platform === "win32"
        ? path.join(javaHome, "bin", "java.exe")
        : path.join(javaHome, "bin", "java");

    if (fs.existsSync(javaExe)) return javaExe;
  }
  return process.platform === "win32" ? "java.exe" : "java";
}

function resolveBackendJar() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, "backend", "app.jar");
  }
  return path.join(
    __dirname,
    "..",
    "..",
    "backend",
    "target",
    "itinerary-backend-0.0.1-SNAPSHOT.jar"
  );
}

function checkBackendHealth() {
  return new Promise((resolve) => {
    const req = http.get("http://localhost:8080/actuator/health", (res) => {
      resolve(res.statusCode >= 200 && res.statusCode < 300);
      res.resume();
    });
    req.on("error", () => resolve(false));
    req.setTimeout(1300, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend(maxAttempts = 35) {
  for (let i = 0; i < maxAttempts; i++) {
    const healthy = await checkBackendHealth();
    if (healthy) return true;
    await new Promise((r) => setTimeout(r, 1000));
  }
  return false;
}

async function startBackend() {
  const alreadyRunning = await checkBackendHealth();
  if (alreadyRunning) return true;

  const jarPath = resolveBackendJar();

  if (!fs.existsSync(jarPath)) {
    dialog.showErrorBox(
      "Backend Missing",
      `Could not find backend jar:\n${jarPath}\n\nRun npm run backend:jar first.`
    );
    return false;
  }

  const javaCmd = resolveJavaCommand();

  backendProcess = spawn(javaCmd, ["-jar", jarPath], {
    stdio: "ignore",
    detached: false,
    env: { ...process.env, PORT: "8080" }
  });

  backendProcess.on("error", () => {
    dialog.showErrorBox(
      "Java Runtime Error",
      "Failed to start backend. Install Java 21 and set JAVA_HOME."
    );
  });

  return waitForBackend();
}

/* ---------- FORCE KILL BACKEND ---------- */
function stopBackend() {
  try {
    if (backendProcess && !backendProcess.killed) {
      backendProcess.kill("SIGKILL");
    }

    // Extra Windows safety
    if (process.platform === "win32") {
      spawn("taskkill", ["/pid", backendProcess?.pid, "/f", "/t"]);
    }
  } catch (e) {}
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    autoHideMenuBar: true,
    title: "India AI Itinerary Planner",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  const indexPath = path.join(__dirname, "..", "dist", "index.html");
  mainWindow.loadFile(indexPath);
}

app.whenReady().then(async () => {
  const ok = await startBackend();
  if (!ok) return app.quit();
  createWindow();
});

/* ---------- CLEAN EXIT ---------- */
app.on("before-quit", stopBackend);
app.on("window-all-closed", () => {
  stopBackend();
  if (process.platform !== "darwin") app.quit();
});
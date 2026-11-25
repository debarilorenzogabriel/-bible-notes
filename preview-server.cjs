const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = 3000;
const HOST = "0.0.0.0";
const ROOT_DIR = path.join(__dirname, "www");
const WORKSPACE_DIR = __dirname;

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

const server = http.createServer((req, res) => {
  const reqPath = decodeURIComponent((req.url || "/").split("?")[0]);
  const safePath = reqPath === "/" ? "/index.html" : reqPath;

  let filePath = path.normalize(path.join(ROOT_DIR, safePath));
  if (!filePath.startsWith(ROOT_DIR)) {
    res.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Forbidden");
    return;
  }

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    const workspaceCandidate = path.normalize(path.join(WORKSPACE_DIR, safePath));
    if (
      workspaceCandidate.startsWith(path.join(WORKSPACE_DIR, "icons")) &&
      fs.existsSync(workspaceCandidate) &&
      fs.statSync(workspaceCandidate).isFile()
    ) {
      filePath = workspaceCandidate;
    } else {
      filePath = path.join(ROOT_DIR, "index.html");
    }
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || "application/octet-stream";

  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Internal Server Error");
      return;
    }
    res.writeHead(200, { "Content-Type": contentType });
    res.end(content);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`Preview server listening on http://${HOST}:${PORT}`);
});

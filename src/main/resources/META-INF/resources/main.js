const form = document.getElementById("analysis-form");
const statusBox = document.getElementById("status");
const payloadPreview = document.getElementById("payload-preview");
const submitButton = document.getElementById("run-analysis");
const prefillButton = document.getElementById("prefill-demo");
const clearButton = document.getElementById("clear-form");
const remoteRepoFieldsNodes = document.querySelectorAll(".remote-repo-fields");
const localRepoFieldsNodes = document.querySelectorAll(".local-repo-fields");

const SAMPLE_VALUES = {
  repoRemoteUrl: "",
  branch: "main",
  includeInAnalysisExpressions: "",
  excludeFromAnalysisExpressions: "",
  applicationName: "default-application-name",
  applicationRoot: "",
  startCommit: "",
  endCommit: "",
  landscapeToken: "mytokenvalue",
  fetchEndDate: "",
  socialDataTimeFrameDays: "",
};

const DEFAULT_STATUS = "Waiting for input…";

function collectPayload(formData) {
  const payload = {};
  for (const [key, value] of formData.entries()) {
    if (!value || key === "repoType") {
      continue;
    }
    payload[key] = value.trim();
  }

  payload.calculateMetrics = formData.get("calculateMetrics") !== null;
  payload.sendToRemote = formData.get("sendToRemote") !== null;
  payload.fetchSocialData = formData.get("fetchSocialData") !== null;

  if (payload.commitAnalysisLimit) {
    payload.commitAnalysisLimit = parseInt(payload.commitAnalysisLimit);
  }
  if (payload.socialDataTimeFrameDays) {
    payload.socialDataTimeFrameDays = parseInt(payload.socialDataTimeFrameDays);
  }

  return payload;
}

function updatePreview(payload) {
  payloadPreview.textContent = JSON.stringify(payload, null, 2);
}

function setStatus(message = DEFAULT_STATUS, variant = "") {
  statusBox.textContent = message;
  statusBox.className = variant ? `status ${variant}` : "status";
}

async function triggerAnalysis(payload) {
  const response = await fetch("/api/analysis/trigger", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || "Analysis failed");
  }
  return text || "Analysis completed successfully";
}

function handleInput() {
  const formData = new FormData(form);
  updatePreview(collectPayload(formData));
}

async function handleSubmit(event) {
  event.preventDefault();
  const formData = new FormData(form);
  const payload = collectPayload(formData);
  updatePreview(payload);

  if (!payload.repoPath && !payload.repoRemoteUrl) {
    setStatus("Provide either a local repository path or a remote URL.", "error");
    return;
  }

  submitButton.disabled = true;
  setStatus("Triggering analysis…");

  try {
    const message = await triggerAnalysis(payload);
    setStatus(message, "success");
  } catch (error) {
    setStatus(error.message, "error");
  } finally {
    submitButton.disabled = false;
  }
}

function applySample() {
  Object.entries(SAMPLE_VALUES).forEach(([key, value]) => {
    const input = form.elements.namedItem(key);
    if (input) {
      input.value = value;
    }
  });
  form.elements.calculateMetrics.checked = true;
  form.elements.sendToRemote.checked = true;
  form.elements.fetchSocialData.checked = false;

  handleInput();
  setStatus("Sample payload applied.", "success");
}

function resetForm() {
  form.reset();
  updateRepoVisibility();
  updatePreview({});
  setStatus();
}

function updateRepoVisibility() {
  const repoType = form.elements.repoType.value;
  const isRemote = repoType === "remote";

  remoteRepoFieldsNodes.forEach((node) => {
    node.style.display = isRemote ? "grid" : "none";
  });
  localRepoFieldsNodes.forEach((node) => {
    node.style.display = isRemote ? "none" : "grid";
  });
}

form.addEventListener("input", handleInput);
form.addEventListener("change", (e) => {
  if (e.target.name === "repoType") {
    updateRepoVisibility();
    handleInput();
  }
});
form.addEventListener("submit", handleSubmit);
prefillButton?.addEventListener("click", applySample);
clearButton?.addEventListener("click", resetForm);

updatePreview({});
setStatus();

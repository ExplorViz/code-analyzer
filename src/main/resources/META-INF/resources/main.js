const form = document.getElementById("analysis-form");
const statusBox = document.getElementById("status");
const payloadPreview = document.getElementById("payload-preview");
const submitButton = document.getElementById("run-analysis");
const prefillButton = document.getElementById("prefill-demo");
const clearButton = document.getElementById("clear-form");
const remoteRepoFieldsNodes = document.querySelectorAll(".remote-repo-fields");
const localRepoFieldsNodes = document.querySelectorAll(".local-repo-fields");
const localRepositorySelect = document.getElementById("local-repository-select");
const localBranchSelect = document.getElementById("local-branch-select");
const remoteBranchField = document.querySelector(".remote-branch-field");
const remoteBranchInput = remoteBranchField?.querySelector("input");
const localBranchField = document.querySelector(".local-branch-field");
const applicationRowsContainer = document.getElementById("application-rows");
const addApplicationButton = document.getElementById("add-application");

const SAMPLE_VALUES = {
  repoRemoteUrl: "",
  branch: "main",
  includeInAnalysisExpressions: "",
  excludeFromAnalysisExpressions: "",
  startCommit: "",
  endCommit: "",
  landscapeToken: "mytokenvalue",
};

const SAMPLE_APPLICATION_ROWS = [
  { name: "default-application-name", root: "" },
  { name: "second-service", root: "services/second" },
];

const DEFAULT_STATUS = "Waiting for input…";
const LOCAL_REPOSITORIES_ENDPOINT = "/api/analysis/local-repositories";
let localRepositories = [];

function collectApplications() {
  const rows = document.querySelectorAll("[data-application-row]");
  const applications = [];
  rows.forEach((row) => {
    const name = row.querySelector(".app-name-input")?.value.trim() ?? "";
    const root = row.querySelector(".app-root-input")?.value.trim() ?? "";
    if (name) {
      applications.push({ name, root });
    }
  });
  return applications;
}

function addApplicationRow(name = "", root = "") {
  if (!applicationRowsContainer) {
    return;
  }
  const row = document.createElement("div");
  row.className = "application-row";
  row.dataset.applicationRow = "";

  const nameLabel = document.createElement("label");
  nameLabel.innerHTML = `<span class="label-header">Application name <span class="hint">(required)</span></span>`;
  const nameInput = document.createElement("input");
  nameInput.type = "text";
  nameInput.className = "app-name-input";
  nameInput.placeholder = "e.g. order-service";
  nameInput.value = name;
  nameLabel.appendChild(nameInput);

  const rootLabel = document.createElement("label");
  rootLabel.innerHTML = `<span class="label-header">Application root</span>`;
  const rootInput = document.createElement("input");
  rootInput.type = "text";
  rootInput.className = "app-root-input";
  rootInput.placeholder = "relative to repo root, optional";
  rootInput.value = root;
  rootLabel.appendChild(rootInput);

  const actions = document.createElement("div");
  actions.className = "application-row-actions";
  const removeButton = document.createElement("button");
  removeButton.type = "button";
  removeButton.className = "secondary remove-application";
  removeButton.textContent = "Remove";
  actions.appendChild(removeButton);

  row.appendChild(nameLabel);
  row.appendChild(rootLabel);
  row.appendChild(actions);

  applicationRowsContainer.appendChild(row);

  removeButton.addEventListener("click", () => {
    if (applicationRowsContainer.querySelectorAll("[data-application-row]").length <= 1) {
      setStatus("Keep at least one application row.", "error");
      return;
    }
    row.remove();
    handleInput();
  });

  nameInput.addEventListener("input", handleInput);
  rootInput.addEventListener("input", handleInput);
}

function resetApplicationRows() {
  if (!applicationRowsContainer) {
    return;
  }
  applicationRowsContainer.replaceChildren();
  addApplicationRow("default-application-name", "");
}

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

  if (payload.commitAnalysisLimit) {
    payload.commitAnalysisLimit = parseInt(payload.commitAnalysisLimit);
  }

  const applications = collectApplications();
  if (applications.length > 0) {
    payload.applications = applications;
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

function replaceSelectOptions(select, options, placeholder) {
  select.replaceChildren();

  const placeholderOption = document.createElement("option");
  placeholderOption.value = "";
  placeholderOption.textContent = placeholder;
  select.appendChild(placeholderOption);

  options.forEach((optionValue) => {
    const option = document.createElement("option");
    option.value = optionValue;
    option.textContent = optionValue;
    select.appendChild(option);
  });
}

function replaceLocalRepositoryOptions(repositories, placeholder) {
  localRepositorySelect.replaceChildren();

  const placeholderOption = document.createElement("option");
  placeholderOption.value = "";
  placeholderOption.textContent = placeholder;
  localRepositorySelect.appendChild(placeholderOption);

  repositories.forEach((repository) => {
    const option = document.createElement("option");
    option.value = repository.path;
    option.textContent = repository.path;
    localRepositorySelect.appendChild(option);
  });
}

function updateLocalBranchOptions() {
  if (!localBranchSelect) {
    return;
  }

  const selectedRepository = localRepositories.find((repository) => repository.path === localRepositorySelect.value);
  if (!selectedRepository) {
    localBranchSelect.disabled = true;
    replaceSelectOptions(localBranchSelect, [], "Select a repository first");
    return;
  }

  if (selectedRepository.branches.length === 0) {
    localBranchSelect.disabled = true;
    replaceSelectOptions(localBranchSelect, [], "No branches found");
    return;
  }

  replaceSelectOptions(localBranchSelect, selectedRepository.branches, "Use current branch");
  localBranchSelect.disabled = false;
}

async function loadLocalRepositories() {
  if (!localRepositorySelect) {
    return;
  }

  localRepositorySelect.disabled = true;
  replaceLocalRepositoryOptions([], "Loading cloned repositories...");

  try {
    const response = await fetch(LOCAL_REPOSITORIES_ENDPOINT);
    if (!response.ok) {
      throw new Error("Unable to load cloned repositories.");
    }

    localRepositories = await response.json();
    if (localRepositories.length === 0) {
      replaceLocalRepositoryOptions([], "No cloned repositories found");
      updateLocalBranchOptions();
      return;
    }

    replaceLocalRepositoryOptions(localRepositories, "Select a cloned repository");
    localRepositorySelect.disabled = false;
    updateLocalBranchOptions();
  } catch (error) {
    localRepositories = [];
    replaceLocalRepositoryOptions([], error.message);
    updateLocalBranchOptions();
    if (form.elements.repoType.value === "local") {
      setStatus(error.message, "error");
    }
  } finally {
    handleInput();
  }
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

  if (!payload.applications || payload.applications.length === 0) {
    setStatus("Add at least one application with a non-empty name.", "error");
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

  if (applicationRowsContainer) {
    applicationRowsContainer.replaceChildren();
    SAMPLE_APPLICATION_ROWS.forEach((row) => addApplicationRow(row.name, row.root));
  }

  handleInput();
  setStatus("Sample payload applied.", "success");
}

function resetForm() {
  form.reset();
  resetApplicationRows();
  updateRepoVisibility();
  loadLocalRepositories();
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
  if (remoteBranchField) {
    remoteBranchField.style.display = isRemote ? "flex" : "none";
  }
  if (localBranchField) {
    localBranchField.style.display = isRemote ? "none" : "flex";
  }
  if (remoteBranchInput) {
    remoteBranchInput.disabled = !isRemote;
  }
  if (localBranchSelect) {
    localBranchSelect.disabled = isRemote || !localRepositorySelect?.value;
  }

  if (!isRemote) {
    updateLocalBranchOptions();
  }
}

form.addEventListener("input", handleInput);
form.addEventListener("change", (e) => {
  if (e.target.name === "repoType") {
    updateRepoVisibility();
    if (e.target.value === "local") {
      loadLocalRepositories();
    }
    handleInput();
  } else if (e.target === localRepositorySelect) {
    updateLocalBranchOptions();
    handleInput();
  }
});
form.addEventListener("submit", handleSubmit);
prefillButton?.addEventListener("click", applySample);
clearButton?.addEventListener("click", resetForm);
addApplicationButton?.addEventListener("click", () => {
  addApplicationRow();
  handleInput();
});

resetApplicationRows();
updatePreview({});
setStatus();
loadLocalRepositories();

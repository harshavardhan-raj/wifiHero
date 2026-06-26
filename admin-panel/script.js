// Password Hash to match: SHA-256 hash of "harshaThe legend@123"
const ADMIN_PASSWORD_HASH = "6597c14e6004983b1b0ecdd19e4c4af4c5c892e1c5ef2e564cad839cd8a73387";

// Default State (Initial Data Matches config/wifi-config.json)
let configState = {
    version: 1,
    ssidPrefix: "Amrita",
    bands: ["2.4G", "5G"],
    password: "Amrita@2024",
    security: "WPA2",
    floors: [
        { floor: 1, startRoom: 101, endRoom: 130 },
        { floor: 2, startRoom: 201, endRoom: 230 },
        { floor: 3, startRoom: 301, endRoom: 330 },
        { floor: 4, startRoom: 401, endRoom: 430 },
        { floor: 5, startRoom: 501, endRoom: 530 },
        { floor: 6, startRoom: 601, endRoom: 630 },
        { floor: 7, startRoom: 701, endRoom: 730 },
        { floor: 8, startRoom: 801, endRoom: 830 },
        { floor: 9, startRoom: 901, endRoom: 930 },
        { floor: 10, startRoom: 1001, endRoom: 1030 }
    ],
    appUpdate: {
        latestVersion: "1.0.0",
        apkUrl: "",
        changelog: "Initial release — one-tap WiFi registration for all hostel rooms"
    }
};

// Cryptographic hash function for login check
async function sha256(message) {
    const msgBuffer = new TextEncoder().encode(message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// UI Elements & State
document.addEventListener("DOMContentLoaded", () => {
    // Check Session
    if (sessionStorage.getItem("isAdminLoggedIn") === "true") {
        showDashboard();
    }

    // Toggle Password Visibility
    const togglePasswordBtn = document.getElementById("toggle-password");
    const passwordInput = document.getElementById("password");
    togglePasswordBtn.addEventListener("click", () => {
        const type = passwordInput.getAttribute("type") === "password" ? "text" : "password";
        passwordInput.setAttribute("type", type);
        const icon = togglePasswordBtn.querySelector("i");
        icon.classList.toggle("fa-eye");
        icon.classList.toggle("fa-eye-slash");
    });

    // Login Form Submit
    const loginForm = document.getElementById("login-form");
    const loginError = document.getElementById("login-error");
    loginForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const inputPassword = passwordInput.value;
        const hashed = await sha256(inputPassword);
        
        if (hashed === ADMIN_PASSWORD_HASH) {
            sessionStorage.setItem("isAdminLoggedIn", "true");
            loginError.classList.add("hidden");
            showDashboard();
            showToast("Authenticated successfully!", "fa-circle-check");
        } else {
            loginError.classList.remove("hidden");
            passwordInput.value = "";
        }
    });

    // Logout Button
    const logoutBtn = document.getElementById("logout-btn");
    logoutBtn.addEventListener("click", () => {
        sessionStorage.removeItem("isAdminLoggedIn");
        document.getElementById("dashboard-container").classList.add("hidden");
        document.getElementById("login-container").classList.remove("hidden");
        showToast("Logged out successfully.", "fa-circle-info");
    });

    // Dynamic SSID preview expansion
    const ssidHeader = document.getElementById("ssid-list-header");
    const ssidContainer = document.getElementById("ssid-list-container");
    const ssidIcon = document.getElementById("ssid-list-icon");
    ssidHeader.addEventListener("click", () => {
        ssidContainer.classList.toggle("collapsed");
        ssidIcon.classList.toggle("rotate");
    });
});

function showDashboard() {
    document.getElementById("login-container").classList.add("hidden");
    document.getElementById("dashboard-container").classList.remove("hidden");
    
    // Load UI fields
    loadConfigToFields();
    renderFloors();
    updateStatsAndPreviews();
    setupDashboardEventListeners();
}

function loadConfigToFields() {
    document.getElementById("ssid-prefix").value = configState.ssidPrefix;
    document.getElementById("band-24").checked = configState.bands.includes("2.4G");
    document.getElementById("band-5").checked = configState.bands.includes("5G");
    document.getElementById("wifi-security").value = configState.security;
    document.getElementById("wifi-password").value = configState.password;
    document.getElementById("app-version").value = configState.appUpdate.latestVersion;
    document.getElementById("apk-url").value = configState.appUpdate.apkUrl;
    document.getElementById("update-changelog").value = configState.appUpdate.changelog;
}

function setupDashboardEventListeners() {
    // Add Floor Button
    const addFloorBtn = document.getElementById("add-floor-btn");
    addFloorBtn.onclick = () => {
        let newFloorNum = 1;
        if (configState.floors.length > 0) {
            newFloorNum = Math.max(...configState.floors.map(f => f.floor)) + 1;
        }
        
        let startRoom = newFloorNum * 100 + 1;
        let endRoom = newFloorNum * 100 + 30;
        
        configState.floors.push({
            floor: newFloorNum,
            startRoom: startRoom,
            endRoom: endRoom
        });
        
        renderFloors();
        updateStatsAndPreviews();
    };

    // Form Field Inputs Change listeners for automatic calculations
    const fields = ['ssid-prefix', 'wifi-password', 'wifi-security', 'app-version', 'apk-url', 'update-changelog'];
    fields.forEach(fieldId => {
        document.getElementById(fieldId).oninput = () => {
            syncStateFromInputs();
            updateStatsAndPreviews();
        };
    });

    // Checkboxes change listeners
    document.getElementById("band-24").onchange = () => { syncStateFromInputs(); updateStatsAndPreviews(); };
    document.getElementById("band-5").onchange = () => { syncStateFromInputs(); updateStatsAndPreviews(); };

    // Action buttons
    document.getElementById("btn-generate-json").onclick = () => {
        syncStateFromInputs();
        updateStatsAndPreviews();
        showToast("JSON generated!", "fa-code");
    };

    document.getElementById("btn-download-config").onclick = () => {
        syncStateFromInputs();
        const jsonStr = generateJSONString();
        const blob = new Blob([jsonStr], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "wifi-config.json";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showToast("Configuration downloaded!", "fa-file-arrow-down");
    };

    document.getElementById("btn-copy-config").onclick = () => {
        syncStateFromInputs();
        const jsonStr = generateJSONString();
        navigator.clipboard.writeText(jsonStr).then(() => {
            showToast("Copied to clipboard!", "fa-copy");
        }).catch(err => {
            console.error("Could not copy config: ", err);
        });
    };

    // SSID Search filter
    document.getElementById("ssid-search").oninput = (e) => {
        const query = e.target.value.toLowerCase();
        const badges = document.querySelectorAll(".ssid-badge");
        badges.forEach(badge => {
            const text = badge.textContent.toLowerCase();
            if (text.includes(query)) {
                badge.style.display = "inline-block";
            } else {
                badge.style.display = "none";
            }
        });
    };
}

function syncStateFromInputs() {
    configState.ssidPrefix = document.getElementById("ssid-prefix").value;
    
    configState.bands = [];
    if (document.getElementById("band-24").checked) configState.bands.push("2.4G");
    if (document.getElementById("band-5").checked) configState.bands.push("5G");
    
    configState.security = document.getElementById("wifi-security").value;
    configState.password = document.getElementById("wifi-password").value;
    configState.appUpdate.latestVersion = document.getElementById("app-version").value;
    configState.appUpdate.apkUrl = document.getElementById("apk-url").value;
    configState.appUpdate.changelog = document.getElementById("update-changelog").value;
}

function renderFloors() {
    const floorsContainer = document.getElementById("floors-container");
    floorsContainer.innerHTML = "";

    configState.floors.forEach((floorObj, index) => {
        const floorRow = document.createElement("div");
        floorRow.className = "floor-row";
        floorRow.innerHTML = `
            <span class="floor-label-badge">Floor ${floorObj.floor}</span>
            
            <div class="form-group col-3" style="flex: 1;">
                <input type="number" class="floor-input-num" data-index="${index}" value="${floorObj.floor}" placeholder="Floor" min="1" required>
            </div>
            
            <div class="form-group col-3" style="flex: 1.5;">
                <input type="number" class="floor-input-start" data-index="${index}" value="${floorObj.startRoom}" placeholder="Start Room" required>
            </div>
            
            <div class="form-group col-3" style="flex: 1.5;">
                <input type="number" class="floor-input-end" data-index="${index}" value="${floorObj.endRoom}" placeholder="End Room" required>
            </div>
            
            <button type="button" class="btn-delete-floor" data-index="${index}" title="Remove Floor">
                <i class="fa-solid fa-trash"></i>
            </button>
        `;
        floorsContainer.appendChild(floorRow);
    });

    // Attach Floor Input Change listeners
    document.querySelectorAll(".floor-input-num").forEach(input => {
        input.onchange = (e) => {
            const index = parseInt(e.target.dataset.index);
            configState.floors[index].floor = parseInt(e.target.value) || 0;
            updateStatsAndPreviews();
        };
    });

    document.querySelectorAll(".floor-input-start").forEach(input => {
        input.onchange = (e) => {
            const index = parseInt(e.target.dataset.index);
            configState.floors[index].startRoom = parseInt(e.target.value) || 0;
            updateStatsAndPreviews();
        };
    });

    document.querySelectorAll(".floor-input-end").forEach(input => {
        input.onchange = (e) => {
            const index = parseInt(e.target.dataset.index);
            configState.floors[index].endRoom = parseInt(e.target.value) || 0;
            updateStatsAndPreviews();
        };
    });

    // Attach Floor Delete listeners
    document.querySelectorAll(".btn-delete-floor").forEach(btn => {
        btn.onclick = (e) => {
            const index = parseInt(e.currentTarget.dataset.index);
            configState.floors.splice(index, 1);
            renderFloors();
            updateStatsAndPreviews();
        };
    });
}

function generateJSONString() {
    const resultObj = {
        version: configState.version,
        lastUpdated: new Date().toISOString(),
        wifiNetworks: [
            {
                id: "amrita-hostel",
                name: "Amrita Hostel WiFi",
                ssidPrefix: configState.ssidPrefix,
                bands: configState.bands,
                password: configState.password,
                security: configState.security,
                floors: configState.floors
            }
        ],
        appUpdate: configState.appUpdate
    };
    return JSON.stringify(resultObj, null, 2);
}

function updateStatsAndPreviews() {
    // Generate JSON
    const jsonStr = generateJSONString();
    document.getElementById("json-preview").textContent = jsonStr;

    // Calculate stats
    let totalFloors = configState.floors.length;
    let totalRooms = 0;
    let ssids = [];

    configState.floors.forEach(f => {
        if (f.startRoom && f.endRoom && f.endRoom >= f.startRoom) {
            let roomsOnFloor = (f.endRoom - f.startRoom) + 1;
            totalRooms += roomsOnFloor;

            // Generate SSIDs for preview
            for (let r = f.startRoom; r <= f.endRoom; r++) {
                configState.bands.forEach(band => {
                    ssids.push({
                        ssid: `${configState.ssidPrefix}-${band}-${r}`,
                        band: band
                    });
                });
            }
        }
    });

    let totalSSIDs = ssids.length;

    // Update Stats Display
    document.getElementById("stat-floors").textContent = totalFloors;
    document.getElementById("stat-rooms").textContent = totalRooms;
    document.getElementById("stat-ssids").textContent = totalSSIDs;

    // Render SSID Preview List
    const ssidList = document.getElementById("ssid-list");
    ssidList.innerHTML = "";
    
    if (ssids.length === 0) {
        ssidList.innerHTML = `<span class="text-muted">No SSIDs generated</span>`;
    } else {
        // Display first 150 only to keep performance slick, warn if truncated
        const showCount = Math.min(ssids.length, 150);
        for (let i = 0; i < showCount; i++) {
            const item = ssids[i];
            const badge = document.createElement("span");
            badge.className = `ssid-badge ${item.band === '2.4G' ? 'band-24' : 'band-5'}`;
            badge.textContent = item.ssid;
            ssidList.appendChild(badge);
        }
        
        if (ssids.length > 150) {
            const truncationNotice = document.createElement("span");
            truncationNotice.className = "text-muted";
            truncationNotice.style.fontSize = "0.75rem";
            truncationNotice.style.width = "100%";
            truncationNotice.style.marginTop = "0.5rem";
            truncationNotice.textContent = `... and ${ssids.length - 150} more SSIDs (showing first 150 for smooth UI performance)`;
            ssidList.appendChild(truncationNotice);
        }
    }
}

function showToast(message, iconClass = "fa-circle-check") {
    const toast = document.getElementById("toast");
    const toastIcon = document.getElementById("toast-icon");
    const toastMsg = document.getElementById("toast-msg");
    
    // Set icon & text
    toastIcon.className = `fa-solid ${iconClass}`;
    toastMsg.textContent = message;
    
    // Show
    toast.classList.remove("hidden");
    
    // Hide after 3 seconds
    setTimeout(() => {
        toast.classList.add("hidden");
    }, 3000);
}

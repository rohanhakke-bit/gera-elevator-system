const API_BASE = "http://localhost:8080";
const ELEVATOR_CODES = ["A", "B", "C", "D"];
const MOVE_SECONDS_PER_FLOOR = 10;

let totalFloors = 16;
let pollTimer = null;
let tickTimer = null;

const els = {
    floorList: document.getElementById("floorList"),
    shaftList: document.getElementById("shaftList"),
    elevatorPanels: document.getElementById("elevatorPanels"),
    eventLog: document.getElementById("eventLog"),
    connectionBadge: document.getElementById("connectionBadge"),
    resetBtn: document.getElementById("resetBtn"),
    uiTickToggle: document.getElementById("uiTickToggle"),
    emergencyFloor: document.getElementById("emergencyFloor"),
    emergencyDirection: document.getElementById("emergencyDirection"),
    emergencyBtn: document.getElementById("emergencyBtn"),
    rebalanceBtn: document.getElementById("rebalanceBtn"),
    morningBtn: document.getElementById("morningBtn"),
    eveningBtn: document.getElementById("eveningBtn")
};

class ElevatorViewModel {
    constructor(code) {
        this.code = code;
        this.currentFloor = 1;
        this.direction = "IDLE";
        this.doorState = "CLOSED";
        this.status = "ACTIVE";
        this.moveSecondsRemaining = 0;
        this.doorSecondsRemaining = 0;
        this.upStops = [];
        this.downStops = [];
    }

    apply(data) {
        this.currentFloor = data.currentFloor;
        this.direction = data.direction;
        this.doorState = data.doorState;
        this.status = data.status;
        this.moveSecondsRemaining = data.moveSecondsRemaining || 0;
        this.doorSecondsRemaining = data.doorSecondsRemaining || 0;
        this.upStops = data.upStops || [];
        this.downStops = data.downStops || [];
        this.render();
    }

    visualFloor() {
        if (this.direction === "IDLE" || this.moveSecondsRemaining <= 0) {
            return clamp(this.currentFloor, 1, totalFloors);
        }

        const progress = clamp(
            (MOVE_SECONDS_PER_FLOOR - this.moveSecondsRemaining) / MOVE_SECONDS_PER_FLOOR,
            0,
            1
        );

        if (this.direction === "UP") {
            return clamp(this.currentFloor + progress, 1, totalFloors);
        }

        if (this.direction === "DOWN") {
            return clamp(this.currentFloor - progress, 1, totalFloors);
        }

        return clamp(this.currentFloor, 1, totalFloors);
    }

    render() {
        const car = document.querySelector(`[data-car="${this.code}"]`);
        if (car) {
            const maxBottom = 100 - (100 / totalFloors);
            const bottom = ((this.visualFloor() - 1) / (totalFloors - 1)) * maxBottom;
            car.style.bottom = `calc(${bottom}% + 3px)`;
            car.className = `car ${String(this.doorState).toLowerCase()}`;
            car.querySelector(".car-display").textContent =
                `${this.currentFloor} ${directionShort(this.direction)}`;
        }

        const panel = document.querySelector(`[data-panel="${this.code}"]`);
        if (panel) {
            panel.querySelector(".p-direction").textContent = this.direction;
            panel.querySelector(".p-floor").textContent = this.currentFloor;
            panel.querySelector(".p-door").textContent = this.doorState;
            panel.querySelector(".p-status").textContent = this.status;
            panel.querySelector(".p-move").textContent =
                this.moveSecondsRemaining > 0 ? `${this.moveSecondsRemaining}s` : "-";
            panel.querySelector(".p-door-time").textContent =
                this.doorSecondsRemaining > 0 ? `${this.doorSecondsRemaining}s` : "-";
            panel.querySelector(".p-up").textContent = this.upStops.length ? this.upStops.join(", ") : "-";
            panel.querySelector(".p-down").textContent = this.downStops.length ? this.downStops.join(", ") : "-";
        }
    }
}

const elevators = Object.fromEntries(ELEVATOR_CODES.map(code => [code, new ElevatorViewModel(code)]));

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function directionShort(direction) {
    if (direction === "UP") return "UP";
    if (direction === "DOWN") return "DN";
    return "--";
}

async function api(path, options = {}) {
    const response = await fetch(API_BASE + path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || response.statusText);
    }

    return response.json();
}

function buildUi() {
    document.documentElement.style.setProperty("--floors", totalFloors);
    els.emergencyFloor.max = totalFloors;
    buildFloorList();
    buildShafts();
    buildElevatorPanels();
}

function buildFloorList() {
    els.floorList.innerHTML = "";

    for (let floor = totalFloors; floor >= 1; floor--) {
        const row = document.createElement("div");
        row.className = "floor-row";

        const no = document.createElement("div");
        no.className = "floor-no";
        no.textContent = floor;

        const actions = document.createElement("div");
        actions.className = "hall-actions";

        if (floor < totalFloors) {
            actions.appendChild(makeHallButton(floor, "UP", "UP"));
        }

        if (floor > 1) {
            actions.appendChild(makeHallButton(floor, "DOWN", "DN"));
        }

        row.appendChild(no);
        row.appendChild(actions);
        els.floorList.appendChild(row);
    }
}

function makeHallButton(floor, direction, text) {
    const button = document.createElement("button");
    button.className = "small-btn";
    button.type = "button";
    button.textContent = text;
    button.dataset.hall = `${floor}:${direction}`;
    button.addEventListener("click", () => sendExternal(floor, direction));
    return button;
}

function buildShafts() {
    els.shaftList.innerHTML = "";

    ELEVATOR_CODES.forEach(code => {
        const shaft = document.createElement("div");
        shaft.className = "shaft";

        const lines = document.createElement("div");
        lines.className = "shaft-lines";
        for (let i = 0; i < totalFloors; i++) {
            const line = document.createElement("div");
            line.className = "shaft-line";
            lines.appendChild(line);
        }

        const title = document.createElement("div");
        title.className = "shaft-title";
        title.textContent = `Elevator ${code}`;

        const car = document.createElement("div");
        car.className = "car closed";
        car.dataset.car = code;
        car.innerHTML = `
            <div class="car-display">1 --</div>
            <div class="door left"></div>
            <div class="door right"></div>
            <div class="car-id">${code}</div>
        `;

        shaft.appendChild(lines);
        shaft.appendChild(title);
        shaft.appendChild(car);
        els.shaftList.appendChild(shaft);
    });
}

function buildElevatorPanels() {
    els.elevatorPanels.innerHTML = "";

    ELEVATOR_CODES.forEach(code => {
        const card = document.createElement("section");
        card.className = "elevator-card";
        card.dataset.panel = code;

        card.innerHTML = `
            <div class="elevator-card-header">
                <h3>Elevator ${code}</h3>
                <span class="status-pill p-direction">IDLE</span>
            </div>

            <div class="status-grid">
                <div>Floor: <b class="p-floor">1</b></div>
                <div>Door: <b class="p-door">CLOSED</b></div>
                <div>Status: <b class="p-status">ACTIVE</b></div>
                <div>Move: <b class="p-move">-</b></div>
                <div>Door Time: <b class="p-door-time">-</b></div>
                <div>Code: <b>${code}</b></div>
                <div class="queue">UP Stops: <span class="p-up">-</span></div>
                <div class="queue">DOWN Stops: <span class="p-down">-</span></div>
            </div>

            <div class="floor-buttons" data-buttons="${code}"></div>

            <div class="elevator-actions">
                <button class="btn" data-break="${code}">Breakdown</button>
                <button class="btn" data-restore="${code}">Restore</button>
            </div>
        `;

        const buttons = card.querySelector(`[data-buttons="${code}"]`);
        for (let floor = totalFloors; floor >= 1; floor--) {
            const button = document.createElement("button");
            button.className = "floor-btn";
            button.type = "button";
            button.textContent = floor;
            button.dataset.cabin = `${code}:${floor}`;
            button.addEventListener("click", () => sendInternal(code, floor));
            buttons.appendChild(button);
        }

        card.querySelector(`[data-break="${code}"]`).addEventListener("click", () => breakdown(code));
        card.querySelector(`[data-restore="${code}"]`).addEventListener("click", () => restore(code));

        els.elevatorPanels.appendChild(card);
    });
}

async function sendExternal(floor, direction) {
    mark(`[data-hall="${floor}:${direction}"]`, true);

    try {
        const result = await api("/api/requests/external", {
            method: "POST",
            body: JSON.stringify({ floor, direction })
        });
        log(`Floor ${floor} ${direction} assigned to ${result.assignedElevatorCode}`);
        await loadState();
    } catch (error) {
        mark(`[data-hall="${floor}:${direction}"]`, false);
        log(`External failed: ${shortError(error)}`, true);
    }
}

async function sendInternal(elevatorCode, destinationFloor) {
    mark(`[data-cabin="${elevatorCode}:${destinationFloor}"]`, true);

    try {
        await api("/api/requests/internal", {
            method: "POST",
            body: JSON.stringify({ elevatorCode, destinationFloor })
        });
        log(`Elevator ${elevatorCode} destination ${destinationFloor}`);
        await loadState();
    } catch (error) {
        mark(`[data-cabin="${elevatorCode}:${destinationFloor}"]`, false);
        log(`Internal failed: ${shortError(error)}`, true);
    }
}

async function sendEmergency() {
    const floor = Number(els.emergencyFloor.value);
    const direction = els.emergencyDirection.value;

    try {
        const result = await api("/api/requests/emergency", {
            method: "POST",
            body: JSON.stringify({ floor, direction })
        });
        log(`Emergency at floor ${floor} ${direction}, assigned to ${result.assignedElevatorCode}`);
        await loadState();
    } catch (error) {
        log(`Emergency failed: ${shortError(error)}`, true);
    }
}

async function breakdown(code) {
    try {
        await api(`/api/elevators/${code}/breakdown?stuck=false`, { method: "POST" });
        log(`Elevator ${code} marked BROKEN`);
        await loadState();
    } catch (error) {
        log(`Breakdown failed: ${shortError(error)}`, true);
    }
}

async function restore(code) {
    try {
        await api(`/api/elevators/${code}/restore`, { method: "POST" });
        log(`Elevator ${code} restored`);
        await loadState();
    } catch (error) {
        log(`Restore failed: ${shortError(error)}`, true);
    }
}

async function resetSystem() {
    try {
        await api("/api/system/reset", { method: "POST" });
        log("System reset");
        await loadState();
    } catch (error) {
        log(`Reset failed: ${shortError(error)}`, true);
    }
}

async function rebalance() {
    try {
        await api("/api/elevators/rebalance", { method: "POST" });
        log("Idle elevators rebalanced");
        await loadState();
    } catch (error) {
        log(`Rebalance failed: ${shortError(error)}`, true);
    }
}

async function predictive(direction) {
    try {
        await api(`/api/elevators/predictive-position?rushDirection=${direction}`, { method: "POST" });
        log(`Predictive positioning ${direction}`);
        await loadState();
    } catch (error) {
        log(`Predictive failed: ${shortError(error)}`, true);
    }
}

async function tickOneSecond() {
    try {
        await api("/api/simulation/tick?seconds=1", { method: "POST" });
        await loadState();
    } catch {
        setConnected(false);
    }
}

async function loadState() {
    try {
        const state = await api("/api/system/state");
        setConnected(true);

        if (state.totalFloors !== totalFloors) {
            totalFloors = state.totalFloors;
            buildUi();
        }

        state.elevators.forEach(elevator => {
            if (elevators[elevator.code]) {
                elevators[elevator.code].apply(elevator);
            }
        });

        highlightRequests(state.activeRequests || []);
    } catch (error) {
        setConnected(false);
        log(`State failed: ${shortError(error)}`, true);
    }
}

function highlightRequests(requests) {
    document.querySelectorAll(".active").forEach(el => el.classList.remove("active"));

    requests.forEach(request => {
        if ((request.type === "EXTERNAL" || request.type === "EMERGENCY")
            && request.sourceFloor
            && request.direction) {
            mark(`[data-hall="${request.sourceFloor}:${request.direction}"]`, true);
        }

        if (request.type === "INTERNAL"
            && request.assignedElevatorCode
            && request.destinationFloor) {
            mark(`[data-cabin="${request.assignedElevatorCode}:${request.destinationFloor}"]`, true);
        }
    });
}

function mark(selector, active) {
    const el = document.querySelector(selector);
    if (el) {
        el.classList.toggle("active", active);
    }
}

function setConnected(ok) {
    els.connectionBadge.classList.toggle("ok", ok);
    els.connectionBadge.classList.toggle("bad", !ok);
    els.connectionBadge.textContent = ok ? "Connected" : "Disconnected";
}

function log(message, error = false) {
    const line = document.createElement("div");
    line.className = `log-line${error ? " error" : ""}`;
    line.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    els.eventLog.prepend(line);
}

function shortError(error) {
    const text = error.message || String(error);
    try {
        const json = JSON.parse(text);
        return json.detail || json.message || text.slice(0, 160);
    } catch {
        return text.slice(0, 160);
    }
}

function setupEvents() {
    els.resetBtn.addEventListener("click", resetSystem);
    els.emergencyBtn.addEventListener("click", sendEmergency);
    els.rebalanceBtn.addEventListener("click", rebalance);
    els.morningBtn.addEventListener("click", () => predictive("UP"));
    els.eveningBtn.addEventListener("click", () => predictive("DOWN"));

    els.uiTickToggle.addEventListener("change", () => {
        if (tickTimer) {
            clearInterval(tickTimer);
            tickTimer = null;
        }

        if (els.uiTickToggle.checked) {
            tickTimer = setInterval(tickOneSecond, 1000);
            log("UI tick enabled");
        } else {
            log("UI tick disabled. Backend scheduler can run simulation.");
        }
    });
}

function start() {
    buildUi();
    setupEvents();
    loadState();

    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(loadState, 1000);

    log("Frontend ready");
}

start();

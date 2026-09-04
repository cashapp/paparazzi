'use strict';

window.runs = {};

// Root container for all rendered shots; assigned by bootstrap().
let rootContainer;

class Run {
  constructor(id, data) {
    this.id = id;
    this.data = data;
  }
}

class Shot {
  constructor(name, test) {
    this.name = name;
    this.test = test;

    [, this.package, this.clazz, this.method] = Shot.testMethodRegex.exec(test);

    this.runs = [];
  }

  static get testMethodRegex() {
    return /^(.*)\.(.*)#(.*)$/;
  }

  addRun(runId, file, timestamp, deltaFile, expectedFile) {
    const run = { id: runId, file, timestamp, deltaFile, expectedFile };
    this.runs.push(run);
    this.displayRun(run);

    const circle = el('div', 'test__details__selector');
    if (deltaFile !== undefined) {
      circle.classList.add('test__details__selector--failed');
    }
    circle.onmouseover = () => {
      this.displayRun(run);
      for (const shot of Object.values(paparazziRenderer.shots)) {
        const otherRun = shot.runs.find((candidate) => candidate.id === runId);
        shot.img.style.opacity = otherRun ? '1' : '0.3';
        if (otherRun) {
          shot.displayRun(otherRun);
        }
      }
    };
    this.overlayDiv.appendChild(circle);
  }

  displayRun(run) {
    if (run.deltaFile !== undefined) {
      // Failed run: show the expected/actual/delta comparison instead of the single image.
      this.displayFailure(run);
    } else {
      this.displayMedia(run.file);
    }
    this.timestampP.innerText = run.timestamp;
  }

  displayFailure(run) {
    this.screenDiv.classList.add('screen--failed');
    this.img.style.display = 'none';
    this.video.style.display = 'none';
    this.renderDiff(run.expectedFile, run.file, run.deltaFile);
  }

  displayMedia(file) {
    this.screenDiv.classList.remove('screen--failed');
    this.hideDiff();
    if (file.endsWith('.png')) {
      this.img.src = file;
      this.img.style.display = 'inline';
      this.video.style.display = 'none';
    } else {
      this.video.src = file;
      this.video.style.display = 'inline';
      this.img.style.display = 'none';
    }
  }

  renderDiff(expectedFile, actualFile, deltaFile) {
    if (this.diffDiv === undefined) {
      this.diffDiv = el('div', 'shot__diff');
      this.screenDiv.appendChild(this.diffDiv);
    }
    this.diffDiv.style.display = 'flex';
    this.diffDiv.innerHTML = '';

    const panels = [
      ['expected', expectedFile],
      ['actual', actualFile],
      ['delta', deltaFile]
    ];
    for (const [label, src] of panels) {
      const img = el('img');
      img.src = src;
      const figure = el('figure');
      figure.appendChild(img);
      figure.appendChild(el('figcaption', null, label));
      this.diffDiv.appendChild(figure);
    }
  }

  hideDiff() {
    if (this.diffDiv !== undefined) {
      this.diffDiv.style.display = 'none';
    }
  }

  removeRun(runId) {
    const index = this.runs.findIndex((run) => run.id === runId);
    if (index !== -1) {
      this.runs.splice(index, 1);
    }
  }

  inflate() {
    const screenDiv = el('div', 'screen');
    rootContainer.appendChild(screenDiv);
    this.screenDiv = screenDiv;

    const img = el('img');
    const video = el('video');
    video.autoplay = 'autoplay';
    video.muted = 'muted';
    video.loop = 'loop';

    const overlayDiv = el('div', 'overlay');
    screenDiv.appendChild(img);
    screenDiv.appendChild(video);
    screenDiv.appendChild(overlayDiv);

    screenDiv.onmouseover = () => overlayDiv.classList.add('overlay__hovered');
    screenDiv.onmouseout = () => overlayDiv.classList.remove('overlay__hovered');

    const nameP = el('p', 'test__details test__details__name', this.method);
    if (this.name !== undefined) {
      nameP.innerText += ` ${this.name}`;
    }
    const classP = el('p', 'test__details test__details__class', this.clazz);
    const packageP = el('p', 'test__details test__details__package', this.package);
    const timestampP = el('p', 'test__details test__details__timestamp');

    overlayDiv.appendChild(nameP);
    overlayDiv.appendChild(classP);
    overlayDiv.appendChild(packageP);
    overlayDiv.appendChild(timestampP);

    // Keep references to the DOM for later updates.
    this.img = img;
    this.video = video;
    this.timestampP = timestampP;
    this.overlayDiv = overlayDiv;
  }
}

class PaparazziRenderer {
  constructor() {
    // Used for content comparison so we only re-render the updated runs.
    this.currentRuns = {};
    // Runs we know won't be updated anymore.
    this.lockedRunIds = [];
    this.shots = {}; // Key is `${test}${name}`, value is a Shot.
  }

  start() {
    this.loadRunScript('index.js');
    for (const runId of window.all_runs) {
      this.loadRunScript(`runs/${runId}.js`);
    }
    setInterval(() => this.refresh(), 100);
  }

  render(run) {
    const previous = this.currentRuns[run.id];
    if (previous && JSON.stringify(previous) === JSON.stringify(run)) {
      // This run didn't change.
      return;
    }
    this.currentRuns[run.id] = run;

    for (const datum of run.data) {
      const key = `${datum.testName}${datum.name}`;
      let shot = this.shots[key];
      if (!shot) {
        shot = new Shot(datum.name, datum.testName);
        this.shots[key] = shot;
        shot.inflate();
      }

      shot.addRun(run.id, datum.file, datum.timestamp, datum.deltaFile, datum.expectedFile);

      // TODO: setup listeners for filters/hovering, etc.
    }
  }

  renderAll() {
    this.loadRunScript('index.js');
    for (const runId of window.all_runs) {
      if (this.lockedRunIds.includes(runId)) {
        continue;
      }
      // Script loading is async, so the rendering happens on the next refresh.
      this.loadRunScript(`runs/${runId}.js`);

      this.render(new Run(runId, window.runs[runId]));

      const lastRunId = window.all_runs[window.all_runs.length - 1];
      if (runId !== lastRunId) {
        // This run isn't the last one, so we know it won't be updated anymore.
        this.lockedRunIds.push(runId);
        delete this.currentRuns[runId];
      }
    }
  }

  refresh() {
    if (window.all_runs.length === 0) return;
    this.renderAll();
  }

  loadRunScript(js) {
    const script = el('script');
    script.src = js;
    script.onload = () => script.remove();
    document.head.appendChild(script);
  }
}

const paparazziRenderer = new PaparazziRenderer();

function bootstrap() {
  rootContainer = document.getElementById('rootContainer');
  paparazziRenderer.start();
}

// Creates an element with an optional class name and text content.
function el(tagName, className, text) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (text !== undefined) {
    element.innerText = text;
  }
  return element;
}

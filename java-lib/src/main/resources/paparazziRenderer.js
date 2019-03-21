window.runs = {};

class Run {
  constructor(id, data) {
    this.id = id
    // TODO(oldergod) which entries are required/optional?
    this.data = data // These are not Shot object..., they are the run.js.
  }
}

// Data representation of a Shot.
// 'shot': {
//   'name': 'idle',
//   'method': "active",
//   'class': "BoostView",
//   'package': "com.squareup.cash.boost",
//   'runs': [
//     {
//       'run': '20190319153912aaab',
//       'file': 'boost.webp',
//       'timestamp': '16:13:48',
//     },
//     {
//       'run': '20190319153912cced',
//       'file': 'boost.webp',
//       'timestamp': '16:13:48'
//     },
//     {
//       'run': '20190319153912oooz',
//       'file': 'boost.webp',
//       'timestamp': '16:13:48'
//     }
//   ]
// }

class Shot {
  constructor(name, test) {
    this.name = name;
    this.test = test;

    var testMethodRegex = /^(.*)\.([^.]*)#([^.]*)$/;
    [, this.package, this.clazz, this.method] = testMethodRegex.exec(test);

    this.runs = [];
  }

  addRun(runId, file, timestamp) {
    // TODO(oldergod) do we need to sort?
    this.runs.push(
      {
        'id': runId,
        'file': file,
        'timestamp': timestamp
      }
    )

    this.img.src = runId + '/' + file
    this.timestampP.innerText = timestamp

    var _this = this;
    const circle = document.createElement('div');
    circle.classList.add('test__details__selector', `run-${runId}`);
    circle.onmouseover = function(e) {
      _this.img.src = runId + '/' + file;

      for(var shot of Object.values(paparazziRenderer.shots)) {
        var found = false
        for(var run of shot.runs) {
          if (runId == run.id) {
            shot.img.src = run.id + '/' + run.file;
            shot.timestampP.innerText = run.timestamp

            found = true;
            break;
          }
        }
        if (!found) {
          shot.img.style.opacity = "0.9";
        }
      }
    };
    this.overlayDiv.appendChild(circle)
  }

  removeRun(runId) {
    const index = this.runs.indexOf((run) => run.id == runId)
    if (index == -1) return

    this.runs.splice(index, 1)
  }

  inflate() {
    const screenDiv = document.createElement('div')
    screenDiv.classList.add('screen')

    document.rootContainer.appendChild(screenDiv)

    const img = document.createElement('img')

    const overlayDiv = document.createElement('div')
    overlayDiv.classList.add('overlay')

    screenDiv.appendChild(img)
    screenDiv.appendChild(overlayDiv)

    const nameP = document.createElement('p')
    nameP.classList.add('test__details', 'test__details__name')

    const classP = document.createElement('p')
    classP.classList.add('test__details', 'test__details__class')

    const packageP = document.createElement('p')
    packageP.classList.add('test__details', 'test__details__package')

    const timestampP = document.createElement('p')
    timestampP.classList.add('test__details', 'test__details__timestamp')

    overlayDiv.appendChild(nameP)
    overlayDiv.appendChild(classP)
    overlayDiv.appendChild(packageP)
    overlayDiv.appendChild(timestampP)

    nameP.innerText = `${this.method} ${this.name}`
    classP.innerText = `${this.clazz}`
    packageP.innerText = `${this.package}`

    // hold references to the DOM for later updates
    this.img = img
    this.timestampP = timestampP
    this.overlayDiv = overlayDiv
  }
}

class PaparazziRenderer {
  constructor() {
    // Used for content comparison for we only re-render the updated ones.
    this.currentRuns = {}
    // Used to store runs we know won't be updated anymore.
    this.lockedRunIds = []
    this.shots = {} // Key is `${test}${name}`, Value is a Shot.
  }

  start() {
    for (let runId of window.all_runs) {
      this.loadRunScript(runId);
    }
    setInterval(this.refresh.bind(this), 100);
  }

  render(run) {
    if (this.currentRuns[run.id]
      && JSON.stringify(this.currentRuns[run.id]) == JSON.stringify(run)) {
      // This run didn't change.
      return
    }
    this.currentRuns[run.id] = run
    console.log('rendering', run)

    for (let datum of run.data) {
      const key = `${datum.testName}${datum.name}`
      let shot = this.shots[key]
      if (!shot) {
        console.log('New shot detected', shot)
        shot = new Shot(datum.name, datum.testName)
        this.shots[key] = shot
        shot.inflate()
      } else {
        //shot.removeRun(run.id)
      }

      console.log('Adding run to shot', shot)
      shot.addRun(run.id, datum.file, datum.timestamp)

      // TODO setup listeners for filters/hovering, etc
    }
  }

  renderAll() {
    for (let runId in window.runs) {
      if (this.lockedRunIds.includes(runId)) {
        continue
      }
      // The js loading is async so the rendering can happen in the next refresh
      this.loadRunScript(runId)

      this.render(new Run(runId, window.runs[runId]))

      const lastRunId = window.all_runs[window.all_runs.length - 1]
      if (runId != lastRunId) {
        // This run isn't the last run so we know it ain't gonna be updated.
        this.lockedRunIds.push(runId)
        delete this.currentRuns[runId]
      }
    }
  }

  refresh() {
    if (window.all_runs.length == 0) return;

    this.renderAll();
  }

  loadRunScript(runId) {
    const script = document.createElement('script')
    script.src = `${runId}/run.js`
    script.onload = function () {
      this.remove()
    }
    document.head.appendChild(script)
  }
}

const paparazziRenderer = new PaparazziRenderer()
console.log(paparazziRenderer)

function bootstrap() {
  document.rootContainer = document.getElementById('rootContainer');
  paparazziRenderer.start()
}

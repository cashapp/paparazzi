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
    this.name = name
    this.test = test // split into method, class, package
    this.method = "TODO"
    // TODO(oldergod) is it a keyword?
    this.class = "TODO"
    this.package = "TODO"
    this.runs = []
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
  }

  removeRun(runId) {
    const index = this.runs.indexOf((run) => run.id == runId)
    if (index == -1) return

    this.runs.splice(index, 1)
  }

  inflate() {
    const circles = this.runs.map(run => {
      const circle = document.createElement('div')
      circle.classList.add('test__details__selector', `run-${run.id}`)
      circle
    })

    const nameP = document.createElement('p')
    nameP.classList.add('test__details', 'test__details__name')
    nameP.innerText = `${this.method} ${this.name}`

    // TODO create classP
    // TODO create packageP
    // TODO create timestampP

    // TODO create overlayDiv

    overlayDiv.appendChild(nameP)
    overlayDiv.appendChild(classP)
    overlayDiv.appendChild(packageP)
    overlayDiv.appendChild(timestampP)
    circles.forEach(circle => overlayDiv.appendChild(circle))

    // TODO create img

    const div = document.createElement('div')
    div.classList.add('screen')

    div.appendChild(img)
    div.appendChild(overlayDiv)
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
      this.loadRunScript(runId)
    }
    setInterval(this.refresh.bind(this), 1000)
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
      const key = `${datum.test}${datum.name}`
      let shot = this.shots[key]
      if (!shot) { this.shots[key] = new Shot(datum.name, datum.test) }
      shot.removeRun(run.id)
      shot.addRun(run.id, datum.file, datum.timestamp)

      console.log('Gonna render', shot)
      // inflate
      // add to html in a idempotent way (we replace first, fallback to appending)
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
    if (window.all_runs.length == 0) return

    this.renderAll()
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
paparazziRenderer.start()
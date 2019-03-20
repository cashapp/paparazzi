class Run {
  constructor(id, shots) {
    this.id = id
    this.shots = shots
  }
}

function deleteme() {
  const obj = {
    'shot': {
      'name': 'idle',
      'method': "active",
      'class': "BoostView",
      'package': "com.squareup.cash.boost",
      'runs': [
        {
          'run': '20190319153912aaab',
          'file': 'boost.webp',
          'timestamp': '16:13:48',
        },
        {
          'run': '20190319153912cced',
          'file': 'boost.webp',
          'timestamp': '16:13:48'
        },
        {
          'run': '20190319153912oooz',
          'file': 'boost.webp',
          'timestamp': '16:13:48'
        }
      ]
    }
  }
}

class Shot {
  constructor(name, test) {
    this.name = name
    this.test = test // split into method, class, package
    this.runs = []
  }

  add(runId, file, timestamp) {
    this.runs.push(
      {
        'run': runId,
        'file': file,
        'timestamp': timestamp // parse?
      }
    )
  }

  inflate() {
    // create div.screen
    // set img
    // set test details
    // add circles
  }
}

class PaparazziRenderer {
  constructor() {
    this.currentRuns = {}
    this.lockedRunIds = []
  }

  start() {
    for (let runId of window.all_runs) {
      this.loadRunScript(runId)
    }
    setInterval(this.refresh.bind(this), 1000)
    // refresh only last run and rerender
  }

  render(run) {
    if (this.currentRuns[run.id]
      && JSON.stringify(this.currentRuns[run.id]) == JSON.stringify(run)) {
      return
    }
    this.currentRuns[run.id] = run
    console.log('rendering', run)
  }

  renderAll() {
    for (let runId in window.runs) {
      if (this.lockedRunIds.includes(runId)) {
        continue
      }
      this.loadRunScript(runId)

      this.render(new Run(runId, window.runs[runId]))

      const lastRunId = window.all_runs[window.all_runs.length - 1]
      if (runId != lastRunId) {
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
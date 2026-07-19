# Go in 20 Hours — The 80/20 Crash Course

**Goal:** Be productive enough to read, write, debug, and build small Go programs.
**Philosophy:** Go's design already applies the 80/20 rule — it has a small surface area on purpose. This plan teaches the ~20% of that surface area (structs, interfaces, error handling, slices/maps, goroutines/channels at a basic level) that covers the vast majority of real Go code you'll read and write.

Setup once before Hour 1: install Go from https://go.dev/dl, run `go version` to confirm, and pick an editor (VS Code + the official Go extension is the standard choice — it auto-installs `gopls`, `gofmt`, `goimports`, `dlv`).

---

## 1. Concepts Ranked by Practical Value

### Tier 1 — Master these (80% of real Go code)
| Rank | Concept | Why it matters |
|---|---|---|
| 1 | Program structure, packages, `go run`/`go build` | Everything lives inside this shell |
| 2 | Variables, types, `:=` vs `var`, zero values | Go is statically typed; this is the daily grammar |
| 3 | Functions, multiple return values | Go's #1 idiom is `value, err := doThing()` |
| 4 | Error handling (`error` type, `if err != nil`) | This is how Go replaces exceptions — non-negotiable |
| 5 | Slices | The default "list" type; used everywhere, more than arrays |
| 6 | Maps | The default "dictionary" type |
| 7 | Structs | Go's way of building custom data types (no classes) |
| 8 | Methods (functions with a receiver) | How structs get behavior |
| 9 | Pointers (basic: `&`, `*`) | Needed to understand mutation and method receivers |
| 10 | Interfaces (small, implicit) | Go's polymorphism; core to idiomatic Go |
| 11 | Control flow: `if`, `for`, `switch` | Go only has one loop keyword — simple but essential |
| 12 | Packages & modules (`go.mod`, imports) | Every real project is more than one file |

### Tier 2 — Know at a working level (the next 15%)
| Concept | Depth needed |
|---|---|
| Goroutines & channels | Basic concurrency: launch a goroutine, send/receive on a channel, `sync.WaitGroup`. Not: advanced scheduler internals, complex select patterns, lock-free algorithms. |
| Basic file I/O | `os.ReadFile`, `os.WriteFile`, `bufio.Scanner` — not every I/O abstraction in `io`. |
| Testing (`testing` package) | Table-driven tests with `go test`. Not: mocking frameworks, fuzzing, benchmarks (nice-to-know later). |
| Arrays | Just enough to know slices are built on them — you'll rarely use arrays directly. |
| `defer` | Cleanup pattern (`defer file.Close()`) — used constantly, learned in 5 minutes. |
| Constants & `iota` | Enums-via-`iota` shows up in real code (e.g. state machines). |

### Tier 3 — Skip now, revisit later (the remaining 5%, low ROI early)
- Generics (`[T any]`) — genuinely useful, but you can be productive in Go for months without writing one. Learn it once you're comfortable with everything above.
- Reflection (`reflect` package) — rare in application code, common only in library/framework internals.
- `context.Context` deep patterns — know it exists (you'll see it in function signatures), but deep cancellation/timeout patterns can wait.
- Advanced concurrency (`sync.Mutex` internals, atomic ops, worker pool tuning, `select` with multiple cases/timeouts) — come back after Tier 1+2 are solid.
- Build tags, cgo, unsafe, assembly — near-zero ROI for a beginner.
- Channels-as-the-only-tool dogma — many real programs need zero goroutines; don't force concurrency into problems that don't need it.

---

## 2. The 20-Hour Schedule

Ten 2-hour sessions. Each session: **Learn → Code along → Exercise → Common mistakes.** Do the exercises — reading alone will not build the muscle memory.

### Session 1 (Hours 1–2): Setup + Program Structure + Variables
**Learn:**
- `package main`, `func main()`, `import`, `go run file.go`, `go build`
- Variable declaration: `var x int = 5`, `x := 5`, zero values (`0`, `""`, `false`, `nil`)
- Basic types: `int`, `float64`, `string`, `bool`
- `fmt.Println`, `fmt.Printf` with verbs (`%d`, `%s`, `%v`, `%T`)
- Constants: `const Pi = 3.14`, and `iota` for simple enums

**Code along:**
```go
package main

import "fmt"

const (
	StatusPending = iota // 0
	StatusActive         // 1
	StatusDone            // 2
)

func main() {
	var name string = "Ranjith"
	age := 30 // short declaration, type inferred
	fmt.Printf("%s is %d years old, status=%d\n", name, age, StatusActive)
}
```

**Exercise:** Write a program that declares your name, age, and city using both `var` and `:=`, then prints them with `Printf` using at least 3 different verbs (`%s`, `%d`, `%v`).

**Common mistakes:**
- Unused variables/imports cause a **compile error** in Go (not a warning). This is intentional — get used to it early.
- Forgetting `:=` creates a new variable vs `=` which assigns to an existing one — mixing them up in the same scope causes "no new variables on left side of :=".

---

### Session 2 (Hours 3–4): Functions + Conditionals + Loops
**Learn:**
- Function syntax, multiple return values, named returns
- `if`/`else if`/`else` (no parentheses needed, braces mandatory)
- `for` — Go's only loop keyword (covers `while`, classic `for`, infinite loop, `range`)
- `switch` (no fallthrough by default — opposite of C/Java)

**Code along:**
```go
func divide(a, b float64) (float64, error) {
	if b == 0 {
		return 0, fmt.Errorf("cannot divide %v by zero", a)
	}
	return a / b, nil
}

func main() {
	result, err := divide(10, 0)
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Println("Result:", result)

	for i := 0; i < 3; i++ { // classic for
		fmt.Println("count", i)
	}

	n := 5
	switch {
	case n < 0:
		fmt.Println("negative")
	case n == 0:
		fmt.Println("zero")
	default:
		fmt.Println("positive")
	}
}
```

**Exercise:** Write a function `isPrime(n int) bool` and a `main` that loops from 2 to 30 printing every prime using `for` + `if`. Then rewrite the odd/even check inside the loop using `switch` instead of `if`.

**Common mistakes:**
- Forgetting that Go functions returning `(value, error)` should almost always be checked with `if err != nil` immediately — don't let errors flow downstream silently.
- Assuming `switch` falls through like C — it doesn't unless you explicitly write `fallthrough`.

---

### Session 3 (Hours 5–6): Arrays, Slices, Maps
**Learn:**
- Arrays are fixed-size and rarely used directly — know they exist, move on
- Slices: `make([]int, 0, 10)`, `append`, slicing syntax `s[1:3]`, `len`/`cap`
- The mental model: a slice is a *view* (pointer + length + capacity) over an underlying array — this explains most slice "gotchas"
- Maps: `make(map[string]int)`, `m["key"] = value`, `v, ok := m["key"]` (the "comma ok" idiom), `delete(m, "key")`

**Code along:**
```go
func main() {
	nums := []int{1, 2, 3}
	nums = append(nums, 4, 5)
	fmt.Println(nums, len(nums), cap(nums))

	scores := make(map[string]int)
	scores["Alice"] = 90
	scores["Bob"] = 85

	if score, ok := scores["Charlie"]; ok {
		fmt.Println("Charlie's score:", score)
	} else {
		fmt.Println("Charlie not found")
	}

	for name, score := range scores { // map iteration order is NOT guaranteed
		fmt.Println(name, score)
	}
}
```

**Exercise:** Given a slice of words, build a `map[string]int` that counts how many times each word appears (a word-frequency counter). Print the results.

**Common mistakes:**
- Assuming map iteration order is stable — it's intentionally randomized by Go.
- Slicing gotcha: two slices from the same underlying array can silently overwrite each other's data via `append` if capacity allows. If confused later by a bug, this is usually why.
- Comparing a slice to `nil` vs checking `len(s) == 0` — both usually work but know the difference (a `nil` slice has len 0 too).

---

### Session 4 (Hours 7–8): Structs + Methods + Pointers — Mini-Project 1
**Learn:**
- Struct definition and struct literals
- Methods: value receiver `func (p Person) Greet()` vs pointer receiver `func (p *Person) SetName(n string)`
- Rule of thumb: use a pointer receiver if the method needs to mutate the struct, or if the struct is large
- Pointers basics: `&x` (address of), `*p` (dereference)

**Code along:**
```go
type Person struct {
	Name string
	Age  int
}

func (p Person) Greet() string {
	return fmt.Sprintf("Hi, I'm %s", p.Name)
}

func (p *Person) Birthday() {
	p.Age++ // mutates the original struct because receiver is a pointer
}

func main() {
	p := Person{Name: "Ranjith", Age: 29}
	fmt.Println(p.Greet())
	p.Birthday()
	fmt.Println(p.Age) // 30
}
```

**🔨 Mini-Project 1: Contact Book (in-memory)**
Build a CLI-less program (just `main` calling functions) that:
- Defines a `Contact struct { Name, Phone, Email string }`
- Stores contacts in a `[]Contact` or `map[string]Contact`
- Has functions `AddContact`, `FindContact`, `DeleteContact`, `ListContacts`
- Demonstrates both value and pointer receivers somewhere (e.g. `UpdatePhone` mutates via pointer)

**Common mistakes:**
- Forgetting a pointer receiver means changes made inside the method are lost after it returns (classic "why didn't my struct update?" bug).
- Passing large structs by value everywhere instead of by pointer — works, but wastes copies; default to pointer receivers once a struct has more than a couple of fields, for consistency.

---

### Session 5 (Hours 9–10): Interfaces + Error Handling Deep Dive
**Learn:**
- Interfaces are **implicit** — a type satisfies an interface just by implementing its methods, no `implements` keyword
- Why small interfaces are idiomatic Go (`io.Writer` has ONE method: `Write`)
- The `error` interface itself: `type error interface { Error() string }`
- Custom error types, `errors.New`, `fmt.Errorf` with `%w` for wrapping, `errors.Is`/`errors.As`

**Code along:**
```go
type Shape interface {
	Area() float64
}

type Circle struct{ Radius float64 }
type Rectangle struct{ Width, Height float64 }

func (c Circle) Area() float64    { return 3.14159 * c.Radius * c.Radius }
func (r Rectangle) Area() float64 { return r.Width * r.Height }

func printArea(s Shape) { // accepts ANY type with an Area() method
	fmt.Printf("Area: %.2f\n", s.Area())
}

// Custom error
type NotFoundError struct{ Name string }

func (e *NotFoundError) Error() string {
	return fmt.Sprintf("%s not found", e.Name)
}

func findUser(name string) error {
	if name != "admin" {
		return &NotFoundError{Name: name}
	}
	return nil
}

func main() {
	printArea(Circle{Radius: 5})
	printArea(Rectangle{Width: 3, Height: 4})

	if err := findUser("bob"); err != nil {
		var nfe *NotFoundError
		if errors.As(err, &nfe) {
			fmt.Println("Custom handling for:", nfe.Name)
		}
	}
}
```

**Exercise:** Define a `Speaker` interface with `Speak() string`. Create `Dog` and `Cat` structs that implement it differently. Write a function that takes a `[]Speaker` and prints each one speaking. Then write a function that returns an error using `fmt.Errorf("...: %w", err)` to wrap a lower-level error, and unwrap it with `errors.Unwrap` or `errors.Is`.

**Common mistakes:**
- Designing huge interfaces (10+ methods) — idiomatic Go favors small, focused interfaces ("accept interfaces, return structs").
- Comparing errors with `==` instead of `errors.Is` once wrapping is involved — `==` breaks the moment an error gets wrapped.
- Ignoring errors with `_` out of laziness — fine in throwaway scripts, a real bug source in production code.

---

### Session 6 (Hours 11–12): Packages, Modules, File Handling — Mini-Project 2
**Learn:**
- `go mod init modulename`, `go.mod`/`go.sum`, `go mod tidy`
- Multi-file/multi-package project layout (exported `Capitalized` names vs unexported `lowercase`)
- Reading/writing files: `os.ReadFile`, `os.WriteFile`, `bufio.NewScanner` for line-by-line reading
- `defer` for guaranteed cleanup (`defer f.Close()`)

**Code along:**
```go
// file: main.go
package main

import (
	"bufio"
	"fmt"
	"os"
)

func main() {
	f, err := os.Open("input.txt")
	if err != nil {
		fmt.Println("Error opening file:", err)
		return
	}
	defer f.Close() // runs when main() returns, no matter how

	scanner := bufio.NewScanner(f)
	lineCount := 0
	for scanner.Scan() {
		lineCount++
		fmt.Println(scanner.Text())
	}
	fmt.Println("Total lines:", lineCount)
}
```

**🔨 Mini-Project 2: Word Frequency File Analyzer**
- Split into 2 files/packages: a `wordcount` package with a function `CountWords(text string) map[string]int`, and a `main` package that reads a `.txt` file, calls `wordcount.CountWords`, and prints the top 5 most frequent words.
- This forces you to practice: modules, package imports, exported functions, file reading, maps, and sorting a map by value (a genuinely common real-world Go task — sort the keys by slice after converting map entries to a slice of structs).

**Common mistakes:**
- Forgetting exported identifiers must start with a capital letter to be visible from another package — the #1 "why can't I import this function" bug.
- Not calling `defer f.Close()` right after a successful `Open` — leaking file handles.
- Trying to sort a map directly — maps aren't sortable; extract keys/values into a slice first.

---

### Session 7 (Hours 13–14): Goroutines & Channels (Practical Basics)
**Learn:**
- `go someFunction()` launches a goroutine (lightweight thread)
- Channels: `ch := make(chan int)`, `ch <- value` (send), `value := <-ch` (receive) — channels are how goroutines communicate safely
- `sync.WaitGroup` to wait for a group of goroutines to finish
- Buffered vs unbuffered channels (just the practical difference: buffered doesn't block until full)
- **When to actually use this:** I/O-bound work you can parallelize (multiple HTTP calls, multiple file reads) — not CPU work that isn't parallelizable, and not every program needs concurrency at all

**Code along:**
```go
func fetch(url string, ch chan<- string) {
	// pretend this is an HTTP call
	ch <- fmt.Sprintf("fetched %s", url)
}

func main() {
	urls := []string{"a.com", "b.com", "c.com"}
	ch := make(chan string, len(urls)) // buffered so goroutines don't block waiting to send

	var wg sync.WaitGroup
	for _, url := range urls {
		wg.Add(1)
		go func(u string) {
			defer wg.Done()
			fetch(u, ch)
		}(url)
	}

	wg.Wait()
	close(ch)

	for result := range ch {
		fmt.Println(result)
	}
}
```

**Exercise:** Write a program that "fetches" 5 fake URLs concurrently (just `time.Sleep(100 * time.Millisecond)` + return a string), collects results via a channel, and prints the total time taken — compare it against doing the same 5 calls sequentially to see the speedup.

**Common mistakes:**
- Forgetting to close a channel (`close(ch)`) when using `range` over it — the receiving loop will block/deadlock forever waiting for more values.
- Capturing the loop variable incorrectly in a goroutine closure (classic pre-Go-1.22 bug: all goroutines see the *last* value of the loop variable). Passing it as a function parameter (as above) sidesteps this regardless of Go version.
- Reaching for goroutines/channels when a program is simple and sequential is fine — concurrency adds real complexity; only pay for it when you need the speedup.

---

### Session 8 (Hours 15–16): Testing — Mini-Project 3
**Learn:**
- File naming: `xxx_test.go`, function naming: `func TestXxx(t *testing.T)`
- `t.Errorf` / `t.Fatalf` to report failures, `go test ./...` to run all tests
- **Table-driven tests** — the single most important Go testing idiom
- `t.Run` for subtests (gives readable output per case)

**Code along:**
```go
// mathutil.go
package mathutil

func Add(a, b int) int { return a + b }

// mathutil_test.go
package mathutil

import "testing"

func TestAdd(t *testing.T) {
	cases := []struct {
		name     string
		a, b     int
		expected int
	}{
		{"positive numbers", 2, 3, 5},
		{"negative numbers", -2, -3, -5},
		{"zero", 0, 0, 0},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			result := Add(tc.a, tc.b)
			if result != tc.expected {
				t.Errorf("Add(%d, %d) = %d; want %d", tc.a, tc.b, result, tc.expected)
			}
		})
	}
}
```

**🔨 Mini-Project 3: Tested Word-Count Package**
- Go back to Session 6's `wordcount` package and add a table-driven test file for `CountWords`, covering: empty string, single word, repeated words, punctuation/case handling (decide and document the behavior).
- Run with `go test -v ./...` and get everything green.

**Common mistakes:**
- Only testing the "happy path" — always include at least one edge case (empty input, zero, negative, nil) per function.
- Writing one giant test function with many unrelated assertions instead of table-driven subtests — makes failures hard to localize.
- Forgetting `go test` only picks up files ending in `_test.go`.

---

### Sessions 9–10 (Hours 17–20): Final Project
See **Section 6** below for the full spec. Budget these 4 hours as: ~30 min planning/scaffolding, ~2.5 hours building, ~30 min tests, ~30 min polish/README.

---

## 3. What to Learn vs Skip vs Revisit — Quick Reference

| Learn now (Tier 1+2) | Skip for now (Tier 3) | Revisit after this course |
|---|---|---|
| Slices, maps, structs, interfaces | Generics | Generics — once you've written enough repetitive code to *feel* the need |
| Error handling with wrapping | Reflection | `context.Context` cancellation patterns |
| Basic goroutines + channels + WaitGroup | `select` with multiple cases/timeouts | `select`, timeouts, worker pools |
| Table-driven testing | Mocking frameworks, fuzzing | Benchmarks (`go test -bench`), fuzzing |
| `go.mod`, multi-package layout | Build tags, cgo | A real HTTP server (`net/http`), a real DB driver |
| `defer`, basic file I/O | `unsafe`, assembly | `io.Reader`/`io.Writer` composition patterns |

---

## 4. Hands-On Exercises Summary

Every session above already has one embedded. If you want extra reps between sessions, these are good filler (10-20 min each): FizzBuzz, reverse a string, check if a string is a palindrome, find the max/min in a slice, deduplicate a slice, merge two maps, implement a basic stack using a slice.

---

## 5. Mini-Projects (Checkpoints)

1. **Contact Book** (after Session 4) — structs, methods, pointers, slices/maps.
2. **Word Frequency File Analyzer** (after Session 6) — packages, modules, file I/O, maps, sorting.
3. **Tested Word-Count Package** (after Session 8) — table-driven testing on Project 2's code.

Each mini-project reuses and layers on the previous one — by Session 8 you have one small, real, tested Go module, not three disconnected toy scripts.

---

## 6. Final Project (Hours 17–20): Concurrent URL Health Checker CLI

**Why this project:** it forces you to combine *every* Tier 1 concept plus both Tier 2 concepts (goroutines/channels, testing) in one cohesive, genuinely useful tool.

**Spec:**
Build a CLI tool `healthcheck` that:
1. Reads a list of URLs from a text file (one per line) — **file I/O**
2. Defines a `Result struct { URL string; StatusCode int; Err error; Duration time.Duration }` — **structs**
3. Checks each URL concurrently using goroutines + a channel to collect results, bounded by a `sync.WaitGroup` — **goroutines & channels**
4. Uses `net/http` (`http.Get`) to make the request, and Go's error handling to capture failures per URL without crashing the whole program — **error handling**
5. Defines a `Checker` interface with a method `Check(url string) Result`, with a real HTTP implementation and a fake implementation for tests — **interfaces** (this is what makes it testable without real network calls)
6. Prints a summary table sorted by status code, with counts of Up/Down — **slices, maps, sorting**
7. Ships with a `_test.go` file using the fake `Checker` to table-test the summarization/sorting logic without hitting the network — **testing**
8. Is organized as a proper module: `go.mod`, a `checker` package + a `main` package — **packages & modules**

**Acceptance checklist (self-verify you're done):**
- [ ] `go build ./...` succeeds with zero errors
- [ ] `go vet ./...` reports nothing
- [ ] `go test ./...` passes
- [ ] Running it against a file with a mix of valid/invalid URLs prints correct per-URL results and doesn't crash on a failed request
- [ ] Concurrent version is measurably faster than a sequential version for 10+ URLs (print total duration)
- [ ] Code has no unused imports/variables (Go won't compile otherwise, but double check you removed debug prints)
- [ ] README explains how to run it

If you can build this from a blank folder in under 2 hours without copy-pasting from this doc, you've genuinely absorbed the 20%.

---

## 7. Common Beginner Mistakes — Master List

1. **Ignoring `if err != nil`** — check every error where it's returned; don't let it silently propagate as a zero value.
2. **Forgetting exported vs unexported names** (capital = public) — the top cause of confusing import errors.
3. **Value vs pointer receiver confusion** — mutations don't stick without a pointer receiver.
4. **Slice aliasing surprises** — two slices sharing an underlying array can overwrite each other's data through `append`.
5. **Assuming map iteration order is stable** — it isn't, ever.
6. **Comparing wrapped errors with `==`** — use `errors.Is`/`errors.As`.
7. **Deadlocking on unclosed channels** — always `close()` a channel the receiver is `range`-ing over, once no more sends are coming.
8. **Reaching for goroutines when the problem is sequential** — added complexity with no payoff.
9. **Giant interfaces** — Go rewards small, single-purpose interfaces.
10. **Not running `go vet` / `gofmt`** — both are free, instant, and catch real bugs and style drift; run them habitually.

---

## 8. Practice Routine After the 20 Hours

**Weeks 1–2 (cement the basics):**
- Solve 3-5 small problems/day on [Exercism's Go track](https://exercism.org/tracks/go) (has mentored feedback) or [Gophercises](https://gophercises.com/) (free, project-based).
- Re-read [Effective Go](https://go.dev/doc/effective_go) and the [Tour of Go](https://go.dev/tour/) — now that you have hands-on context, both will click differently than a first read.

**Weeks 3–4 (build real things):**
- Build 2-3 small real projects: a CLI todo app with file persistence, a simple REST API with `net/http` (no framework), a basic web scraper.
- Start reading real-world Go source — the Go standard library itself is excellent, idiomatic code (`net/http`, `encoding/json` source are approachable).

**Month 2+ (level up):**
- Learn generics properly once you've felt the pain of writing the same function for `int` and `float64` twice.
- Learn `context.Context` patterns by adding cancellation/timeouts to your health-checker project.
- Pick up a lightweight framework or library relevant to your goals (`net/http` + `chi`/`gin` for APIs, `cobra` for CLIs, `sqlx`/`pgx` for databases).
- Join r/golang or the Gophers Slack, and read a few CVE/postmortem-style Go blog posts (e.g. from the Go blog, or company engineering blogs) to see how real teams use these idioms under pressure.

**Ongoing habit:** write a tiny Go program to solve *some* real annoyance at least once a week (rename files, parse a log, hit an API) — retention comes from use, not review.

**Free resources to bookmark:**
- [go.dev/tour](https://go.dev/tour/) — interactive intro (great for Session 1-6 reinforcement)
- [gobyexample.com](https://gobyexample.com/) — best quick-reference for syntax by topic
- [go.dev/doc/effective_go](https://go.dev/doc/effective_go) — idiomatic style, read after this course, not before
- [exercism.org/tracks/go](https://exercism.org/tracks/go) — practice with feedback
- [gophercises.com](https://gophercises.com/) — free project-based exercises
- [go.dev/blog](https://go.dev/blog/) — official blog, good for leveling up post-course

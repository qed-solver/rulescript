package rulescript_test

import (
	"regexp"
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinUnionTranspose(t *testing.T) {
	rule := loadRule(t, "JoinUnionTranspose")
	e := newEnv(t)
	x, xc := e.scan("t", 1)
	y, yc := e.scan("t", 1)
	o, oc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{xc[0]},
		RightCols: opt.ColList{yc[0]},
		OutCols:   opt.ColList{out},
	}
	jp := &memo.JoinPrivate{}
	onU := e.filters(e.f.ConstructEq(e.v(out), e.v(oc[0])))
	onX := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(oc[0])))

	before := e.f.ConstructInnerJoin(e.f.ConstructUnionAll(x, y, priv), o, onU, jp)
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	ids := regexp.MustCompile(`\((var|item) [0-9]+|\(scan \([0-9,-]+\)\)`)
	normalized := ids.ReplaceAllStringFunc(e.format(got), func(m string) string {
		if m[:5] == "(scan" {
			return "(scan _)"
		}
		return strings.Fields(m)[0] + " _"
	})
	want := "(project pass=() (union-all (inner-join (scan _) (scan _) (filters (filters-item (eq (var _) (var _))))) " +
		"(project pass=() (inner-join (scan _) (scan _) (filters (filters-item (eq (var _) (var _))))) " +
		"(projections (item _ (var _)) (item _ (var _))))) (projections (item _ (var _)) (item _ (var _))))"
	if normalized != want {
		t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), normalized, want)
	}

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(e.f.ConstructUnionAll(x, y, priv), o, onU, jp))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(e.f.ConstructUnion(x, y, priv), o, onU, jp))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(x, o, onX, jp))
}

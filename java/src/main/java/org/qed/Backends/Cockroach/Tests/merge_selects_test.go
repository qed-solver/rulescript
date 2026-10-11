package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestMergeSelects(t *testing.T) {
	rule := loadRule(t, "MergeSelects")
	e := newEnv(t)
	s, c := e.scan("t", 3)

	inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(1))))
	before := e.f.ConstructSelect(inner, e.filters(e.f.ConstructLt(e.v(c[1]), e.num(5))))
	want := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructGt(e.v(c[0]), e.num(1)),
		e.f.ConstructLt(e.v(c[1]), e.num(5)),
	))
	e.expectRewrite(rule, before, want)

	inner2 := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructGt(e.v(c[0]), e.num(1)),
		e.f.ConstructEq(e.v(c[2]), e.num(7)),
	))
	before2 := e.f.ConstructSelect(inner2, e.filters(
		e.f.ConstructLt(e.v(c[1]), e.num(5)),
		e.f.ConstructNe(e.v(c[0]), e.num(3)),
	))
	want2 := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructGt(e.v(c[0]), e.num(1)),
		e.f.ConstructEq(e.v(c[2]), e.num(7)),
		e.f.ConstructLt(e.v(c[1]), e.num(5)),
		e.f.ConstructNe(e.v(c[0]), e.num(3)),
	))
	e.expectRewrite(rule, before2, want2)

	e.expectNoMatch(rule, inner)
	e.expectNoMatch(rule, s)
	proj := e.f.ConstructProject(inner, nil, opt.MakeColSet(c[0], c[1]))
	e.expectNoMatch(rule, proj)
}

func TestSQLMergeSelects(t *testing.T) {
	sql := "SELECT * FROM (SELECT * FROM t WHERE a > 1) WHERE b < 5"
	identityProject := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	before, fired := planFor(t, sql, identityProject)
	if len(fired) != 0 || strings.Count(before, "select") != 2 {
		t.Fatalf("expected two stacked selects without the rule, got %v:\n%s", fired, before)
	}
	got, fired := planFor(t, sql, identityProject, "MergeSelects")
	if strings.Join(fired, ",") != "MergeSelects" || strings.Count(got, "select") != 1 {
		t.Fatalf("expected one merged select, got %v:\n%s", fired, got)
	}
	t.Logf("plan:\n%s", got)
}

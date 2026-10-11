package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinProjectLeft(t *testing.T) {
	rule := loadRule(t, "HoistJoinProjectLeft")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 1)
	leftProject := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0], lc[1]))
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[1]), e.v(rc[0]))) }
	before := e.f.ConstructInnerJoin(leftProject, r, on(), &memo.JoinPrivate{})
	inner := e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0], lc[1], rc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, inner)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(leftProject, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0])), r, on(), &memo.JoinPrivate{}))
}

func TestSQLHoistJoinProjectLeft(t *testing.T) {
	sql := "SELECT q.y, q.x, t.a FROM (SELECT y, x FROM u) AS q JOIN t ON q.y = t.a"
	prune := func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") }
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "inner-join") {
		t.Fatalf("expected an inner join:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "HoistJoinProjectLeft")
	if strings.Join(fired, ",") != "HoistJoinProjectLeft" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}

package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinProjectRight(t *testing.T) {
	rule := loadRule(t, "HoistJoinProjectRight")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 2)
	rightProject := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0], rc[1]))
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))) }
	before := e.f.ConstructLeftJoin(l, rightProject, on(), &memo.JoinPrivate{})
	join := e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(join, nil, opt.MakeColSet(lc[0], rc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, rightProject, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l,
		e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0])), on(), &memo.JoinPrivate{}))
}

func TestSQLHoistJoinProjectRight(t *testing.T) {
	sql := "SELECT t.a, q.y, q.x FROM t LEFT JOIN (SELECT y, x FROM u) AS q ON t.a = q.y"
	prune := func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") }
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "left-join") {
		t.Fatalf("expected a left join:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "HoistJoinProjectRight")
	if strings.Join(fired, ",") != "HoistJoinProjectRight" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}

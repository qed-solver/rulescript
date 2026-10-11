package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateProjectSelect(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProjectSelect")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 2)
	filtered := e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0))))
	rightProject := e.f.ConstructProject(filtered, nil, opt.MakeColSet(rc[1]))
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))) }
	before := e.f.ConstructLeftJoin(l, rightProject, on(), &memo.JoinPrivate{})
	allRight := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0], rc[1]))
	joinOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])),
		e.f.ConstructGt(e.v(rc[0]), e.num(0)))
	join := e.f.ConstructLeftJoin(l, allRight, joinOn, &memo.JoinPrivate{})
	want := e.f.ConstructProject(join, nil, opt.MakeColSet(lc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, filtered, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, rightProject, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l,
		e.f.ConstructProject(filtered, nil, opt.MakeColSet(rc[0])), on(), &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateProjectSelect(t *testing.T) {
	sql := "SELECT t.a, q.y FROM t LEFT JOIN (SELECT y FROM u WHERE x > 0) AS q ON t.a = q.y"
	prune := func(r opt.RuleName) bool {
		return strings.HasPrefix(r.String(), "Prune") || r == opt.EliminateProject
	}
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "left-join") || !strings.Contains(before, "select") {
		t.Fatalf("expected a left join over a filtered project:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "TryDecorrelateProjectSelect")
	if strings.Join(fired, ",") != "TryDecorrelateProjectSelect" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}

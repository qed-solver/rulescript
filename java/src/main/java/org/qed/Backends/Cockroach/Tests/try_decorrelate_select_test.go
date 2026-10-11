package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateSelect(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateSelect")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	filtered := e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0))))
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, filtered, on, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructGt(e.v(rc[0]), e.num(0)),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, filtered, on, &memo.JoinPrivate{}))
	l2, lc2 := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l2, filtered,
		e.filters(e.f.ConstructEq(e.v(lc2[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateSelect(t *testing.T) {
	sql := "SELECT t.a, q.x FROM t JOIN (SELECT x FROM u WHERE x > 0) AS q ON t.a = q.x"
	prune := func(r opt.RuleName) bool {
		return strings.HasPrefix(r.String(), "Prune") || r == opt.EliminateProject
	}
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "select") || !strings.Contains(before, "inner-join") {
		t.Fatalf("expected a filtered join input:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "TryDecorrelateSelect")
	if strings.Join(fired, ",") != "TryDecorrelateSelect" || strings.Contains(got, "select") {
		t.Fatalf("expected the filter absorbed into the join, got %v:\n%s", fired, got)
	}
}

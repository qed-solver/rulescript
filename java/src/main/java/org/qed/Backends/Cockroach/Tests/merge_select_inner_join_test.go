package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMergeSelectInnerJoin(t *testing.T) {
	rule := loadRule(t, "MergeSelectInnerJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	r, rc := e.scan("u", 2)

	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	join := e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(lc[1]), e.v(rc[1]))))
	want := e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructGt(e.v(lc[1]), e.v(rc[1])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	before2 := e.f.ConstructSelect(join, e.filters(
		e.f.ConstructGt(e.v(lc[2]), e.num(0)),
		e.f.ConstructLt(e.v(rc[1]), e.num(9)),
	))
	want2 := e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructGt(e.v(lc[2]), e.num(0)),
		e.f.ConstructLt(e.v(rc[1]), e.num(9)),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before2, want2)

	left := e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(left, e.filters(e.f.ConstructGt(e.v(lc[1]), e.num(0)))))
	e.expectNoMatch(rule, join)
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.f.ConstructGt(e.v(lc[1]), e.num(0)))))
}

func TestSQLMergeSelectInnerJoin(t *testing.T) {
	sql := "SELECT * FROM t, u WHERE t.a = u.x AND t.b > u.y"
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, "select") {
		t.Fatalf("expected a select above the join without the rule:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "MergeSelectInnerJoin")
	if strings.Join(fired, ",") != "MergeSelectInnerJoin" || strings.Contains(got, "select") {
		t.Fatalf("expected the filter inside the join, got %v:\n%s", fired, got)
	}
	t.Logf("plan:\n%s", got)
}

package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushFilterIntoSetOp(t *testing.T) {
	rule := loadRule(t, "PushFilterIntoSetOp")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{out},
	}
	intersection := e.f.ConstructIntersect(l, r, private)
	before := e.f.ConstructSelect(intersection, e.filters(e.f.ConstructGt(e.v(out), e.num(0))))
	lFiltered := e.f.ConstructSelect(l, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(0))))
	rFiltered := e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0))))
	want := e.f.ConstructIntersect(lFiltered, rFiltered, private)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, intersection)
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructUnion(l, r, private),
		e.filters(e.f.ConstructGt(e.v(out), e.num(0)))))
	l2, lc2 := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructIntersect(l2, r,
		&memo.SetPrivate{LeftCols: opt.ColList{lc2[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out}}),
		e.filters(e.f.ConstructGt(e.v(out), e.num(0)))))
}

func TestSQLPushFilterIntoSetOp(t *testing.T) {
	sql := "SELECT a FROM (SELECT a FROM t INTERSECT SELECT x FROM u) AS q WHERE a > 0"
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, "intersect") || !strings.Contains(before, "select") {
		t.Fatalf("expected a filter over intersect:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "PushFilterIntoSetOp")
	if strings.Join(fired, ",") != "PushFilterIntoSetOp" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}

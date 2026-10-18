package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNegateComparison(t *testing.T) {
	rule := loadRule(t, "NegateComparison")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructNot(eq)))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))))
	e.expectRewrite(rule, before, want)

	nearL, nearLC := e.scan("u", 1)
	nearR, nearRC := e.scan("t", 1)
	nearJoin := e.f.ConstructInnerJoin(nearL, nearR, nil, &memo.JoinPrivate{})
	nearNe := e.f.ConstructNe(e.v(nearLC[0]), e.v(nearRC[0]))
	nearBefore := e.f.ConstructSelect(nearJoin, e.filters(e.f.ConstructNot(nearNe)))
	e.expectNoMatch(rule, nearBefore)
}

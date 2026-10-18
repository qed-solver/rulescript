package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToCorrelate(t *testing.T) {
	rule := loadRule(t, "JoinToCorrelate")
	e := newEnv(t)
	left, lc := e.scan("t", 2)
	right, rc := e.scan("nn", 2)
	condition := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(left, right, condition, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoinApply(left, right, condition, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nonMatchLeft, _ := e.scan("t", 2)
	nonMatchRight, _ := e.scan("nn", 2)
	nonMatch := e.f.ConstructLeftJoin(nonMatchLeft, nonMatchRight, condition, &memo.JoinPrivate{})
	e.expectNoMatch(rule, nonMatch)
}

package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyNotInSingleElement(t *testing.T) {
	rule := loadRule(t, "SimplifyNotInSingleElement")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	left := e.v(cols[0])
	right := e.v(cols[1])
	tuple := e.f.ConstructTuple(memo.ScalarListExpr{right}, types.MakeTuple([]*types.T{right.DataType()}))
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNotIn(left, tuple)))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNe(left, right)))
	e.expectRewrite(rule, before, want)

	second := e.num(5)
	multiTuple := e.f.ConstructTuple(memo.ScalarListExpr{right, second}, types.MakeTuple([]*types.T{right.DataType(), second.DataType()}))
	nearMiss := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNotIn(left, multiTuple)))
	e.expectNoMatch(rule, nearMiss)
}

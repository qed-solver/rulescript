package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyInSingleElement(t *testing.T) {
	rule := loadRule(t, "SimplifyInSingleElement")
	e := newEnv(t)

	input1, cols1 := e.scan("t", 2)
	left1 := e.v(cols1[0])
	right1 := e.v(cols1[1])
	tuple1 := e.f.ConstructTuple(memo.ScalarListExpr{right1}, types.MakeTuple([]*types.T{right1.DataType()}))
	before1 := e.f.ConstructSelect(input1, e.filters(e.f.ConstructIn(left1, tuple1)))
	want1 := e.f.ConstructSelect(input1, e.filters(e.f.ConstructEq(e.v(cols1[0]), e.v(cols1[1]))))
	e.expectRewrite(rule, before1, want1)

	input2, cols2 := e.scan("u", 2)
	left2 := e.v(cols2[0])
	right2 := e.v(cols2[1])
	tuple2 := e.f.ConstructTuple(memo.ScalarListExpr{right2}, types.MakeTuple([]*types.T{right2.DataType()}))
	before2 := e.f.ConstructSelect(input2, e.filters(e.f.ConstructIn(left2, tuple2)))
	want2 := e.f.ConstructSelect(input2, e.filters(e.f.ConstructEq(e.v(cols2[0]), e.v(cols2[1]))))
	e.expectRewrite(rule, before2, want2)

	nearInput, nearCols := e.scan("t", 2)
	nearLeft := e.v(nearCols[0])
	nearRight1 := e.v(nearCols[1])
	nearRight2 := e.num(17)
	nearTuple := e.f.ConstructTuple(memo.ScalarListExpr{nearRight1, nearRight2}, types.MakeTuple([]*types.T{nearRight1.DataType(), nearRight2.DataType()}))
	nearMiss := e.f.ConstructSelect(nearInput, e.filters(e.f.ConstructIn(nearLeft, nearTuple)))
	e.expectNoMatch(rule, nearMiss)
}

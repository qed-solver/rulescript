package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushFilterIntoSetOp(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushFilterIntoSetOp")
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	setCol := e.f.Metadata().AddColumn("set", types.Int)
	set := e.f.ConstructIntersect(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{setCol},
	})
	before := e.f.ConstructSelect(set, e.filters(e.f.ConstructEq(e.v(setCol), e.num(7))))
	leftFilter := e.f.ConstructSelect(left, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(7))))
	rightFilter := e.f.ConstructSelect(right, e.filters(e.f.ConstructEq(e.v(rightCols[0]), e.num(7))))
	want := e.f.ConstructIntersect(leftFilter, rightFilter, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{setCol},
	})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss := e.f.ConstructUnion(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{setCol},
	})
	nearMiss = e.f.ConstructSelect(nearMiss, e.filters(e.f.ConstructEq(e.v(setCol), e.num(7))))
	e.expectNoMatch(rule, nearMiss)
}

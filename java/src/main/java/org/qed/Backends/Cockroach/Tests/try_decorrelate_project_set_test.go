package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateProjectSet(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProjectSet")
	e := newEnv(t)
	leftCol := e.f.Metadata().AddColumn("left", types.Int)
	inputCol := e.f.Metadata().AddColumn("input", types.Int)
	emittedCol := e.f.Metadata().AddColumn("emitted", types.Int)
	left := e.intValues([]opt.ColumnID{leftCol}, []int{7}, []int{8})
	input := e.intValues([]opt.ColumnID{inputCol}, []int{3}, []int{4})
	emitted := e.intValues([]opt.ColumnID{emittedCol}, []int{3}, []int{4})
	innerCond := e.filters(e.f.ConstructEq(e.v(inputCol), e.v(emittedCol)))
	projectSet := e.f.ConstructInnerJoin(input, emitted, innerCond, &memo.JoinPrivate{})
	outerCond := e.filters(e.f.ConstructEq(e.v(leftCol), e.v(emittedCol)))
	before := e.f.ConstructInnerJoin(left, projectSet, outerCond, &memo.JoinPrivate{})

	leftAndInput := e.f.ConstructInnerJoin(left, input, nil, &memo.JoinPrivate{})
	wantProjectSet := e.f.ConstructInnerJoin(leftAndInput, emitted, innerCond, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(wantProjectSet, outerCond)
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(left, projectSet, e.filters(e.f.ConstructEq(e.v(leftCol), e.v(inputCol))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}

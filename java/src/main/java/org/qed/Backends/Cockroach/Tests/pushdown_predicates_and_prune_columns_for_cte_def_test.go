package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushdownPredicatesAndPruneColumnsForCTEDef(t *testing.T) {
	rule := loadRule(t, "PushdownPredicatesAndPruneColumnsForCTEDef")
	e := newEnv(t)
	left, lc := e.scan("t", 3)
	right, rc := e.scan("t", 3)
	out := opt.ColList{e.f.Metadata().AddColumn("a", types.Int), e.f.Metadata().AddColumn("b", types.Int), e.f.Metadata().AddColumn("c", types.Int)}
	p1 := e.f.ConstructEq(e.v(lc[0]), e.num(1))
	p2 := e.f.ConstructEq(e.v(rc[1]), e.num(2))
	private := &memo.SetPrivate{LeftCols: opt.ColList(lc), RightCols: opt.ColList(rc), OutCols: out}
	before := e.f.ConstructUnionAll(
		e.f.ConstructSelect(left, e.filters(p1)),
		e.f.ConstructSelect(right, e.filters(p2)),
		private,
	)
	want := e.f.ConstructUnionAll(
		e.f.ConstructSelect(e.f.ConstructSelect(left, e.filters(e.f.ConstructOr(p1, e.f.ConstructEq(e.v(lc[1]), e.num(2))))), e.filters(p1)),
		e.f.ConstructSelect(e.f.ConstructSelect(right, e.filters(e.f.ConstructOr(e.f.ConstructEq(e.v(rc[0]), e.num(1)), p2))), e.filters(p2)),
		private,
	)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearLeft, nlc := e.scan("t", 3)
	nearRight, nrc := e.scan("t", 3)
	nearOut := opt.ColList{e.f.Metadata().AddColumn("a", types.Int), e.f.Metadata().AddColumn("b", types.Int), e.f.Metadata().AddColumn("c", types.Int)}
	nearMiss := e.f.ConstructUnionAll(
		e.f.ConstructSelect(nearLeft, e.filters(e.f.ConstructEq(e.v(nlc[0]), e.num(1)))),
		nearRight,
		&memo.SetPrivate{LeftCols: opt.ColList(nlc), RightCols: opt.ColList(nrc), OutCols: nearOut},
	)
	e.expectNoMatch(rule, nearMiss)
}

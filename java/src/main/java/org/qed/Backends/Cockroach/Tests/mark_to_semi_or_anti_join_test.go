package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMarkToSemiOrAntiJoin(t *testing.T) {
	rule := loadRule(t, "MarkToSemiOrAntiJoin")
	e := newEnv(t)
	md := e.f.Metadata()
	l1, l1c := e.scan("u", 1)
	r1, r1c := e.scan("t", 1)
	l2, l2c := e.scan("u", 1)
	r2, r2c := e.scan("t", 1)
	semi := e.f.ConstructSemiJoin(l1, r1, e.filters(e.f.ConstructEq(e.v(l1c[0]), e.v(r1c[0]))), &memo.JoinPrivate{})
	anti := e.f.ConstructAntiJoin(l2, r2, e.filters(e.f.ConstructEq(e.v(l2c[0]), e.v(r2c[0]))), &memo.JoinPrivate{})
	yes, no := md.AddColumn("yes", types.Bool), md.AddColumn("no", types.Bool)
	semiMarked := e.f.ConstructProject(semi, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), yes),
	}, opt.MakeColSet(l1c[0]))
	antiMarked := e.f.ConstructProject(anti, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), no),
	}, opt.MakeColSet(l2c[0]))
	out, marker := md.AddColumn("out", types.Int), md.AddColumn("marker", types.Bool)
	marked := e.f.ConstructUnionAll(semiMarked, antiMarked, &memo.SetPrivate{
		LeftCols: opt.ColList{l1c[0], yes}, RightCols: opt.ColList{l2c[0], no}, OutCols: opt.ColList{out, marker},
	})
	z := md.AddColumn("z", types.Int)
	before := e.f.ConstructProject(
		e.f.ConstructSelect(marked, e.filters(e.v(marker), e.f.ConstructGt(e.v(out), e.num(0)))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(out)), z)}, opt.ColSet{})
	want := e.f.ConstructProject(
		e.f.ConstructSelect(semi, e.filters(e.f.ConstructGt(e.v(l1c[0]), e.num(0)))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(l1c[0])), z)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(marked, e.filters(e.f.ConstructGt(e.v(out), e.num(0)))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(out)), z)}, opt.ColSet{}))
}

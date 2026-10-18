package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectJoinTranspose(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectJoinTranspose")
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	lp, rp := e.f.Metadata().AddColumn("left-projection", types.Int), e.f.Metadata().AddColumn("right-projection", types.Int)
	lproj := e.f.ConstructProject(left, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), lp),
	}, opt.ColSet{})
	rproj := e.f.ConstructProject(right, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(rc[0]), rp),
	}, opt.ColSet{})
	afterJoin := e.f.ConstructInnerJoin(lproj, rproj,
		e.filters(e.f.ConstructNe(e.v(lp), e.v(rp))), &memo.JoinPrivate{})
	afterCol := e.f.Metadata().AddColumn("after", types.Int)
	want := e.f.ConstructProject(afterJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lp), e.v(rp)), afterCol),
	}, opt.ColSet{})
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), afterCol),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	wide, wc := e.scan("nn3", 3)
	wideJoin := e.f.ConstructInnerJoin(wide, right,
		e.filters(e.f.ConstructNe(e.v(wc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	wideOut := e.f.Metadata().AddColumn("wide-output", types.Int)
	nearMiss := e.f.ConstructProject(wideJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(wc[0]), e.v(rc[0])), wideOut),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}

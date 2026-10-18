package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMergeProjects(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "MergeProjects")

	source, cols := e.scan("t", 1)
	bottomCol := e.f.Metadata().AddColumn("bottom", e.f.Metadata().ColumnMeta(cols[0]).Type)
	inner := e.f.ConstructProject(source, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(2)), bottomCol),
	}, opt.MakeColSet(cols[0]))
	topCol := e.f.Metadata().AddColumn("top", e.f.Metadata().ColumnMeta(cols[0]).Type)
	top := e.f.ConstructPlus(e.v(cols[0]), e.num(1))
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(top, topCol),
	}, opt.ColSet{})

	wantCol := e.f.Metadata().AddColumn("expected", e.f.Metadata().ColumnMeta(cols[0]).Type)
	want := e.f.ConstructProject(source, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), wantCol),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMissTopCol := e.f.Metadata().AddColumn("near_miss_top", e.f.Metadata().ColumnMeta(cols[0]).Type)
	nearMiss := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(bottomCol)), nearMissTopCol),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}

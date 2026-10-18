package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "JoinProjectTranspose")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 2)
	lOut := e.f.Metadata().AddColumn("left", e.f.Metadata().ColumnMeta(lc[0]).Type)
	rOut := e.f.Metadata().AddColumn("right", e.f.Metadata().ColumnMeta(rc[0]).Type)
	lProj := e.f.ConstructProject(l, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), lOut)}, opt.ColSet{})
	rProj := e.f.ConstructProject(r, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(rc[0]), rOut)}, opt.ColSet{})
	on := e.filters(e.f.ConstructEq(e.v(lOut), e.v(rOut)))
	before := e.f.ConstructInnerJoin(lProj, rProj, on, &memo.JoinPrivate{})
	joined := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(joined, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), lOut),
		e.f.ConstructProjectionsItem(e.v(rc[0]), rOut),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	outer := e.f.ConstructLeftJoin(lProj, rProj, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, outer)
}

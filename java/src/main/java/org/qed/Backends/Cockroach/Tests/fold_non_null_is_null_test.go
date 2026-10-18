package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
)

func TestFoldNonNullIsNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullIsNull")
	e := newEnv(t)

	s, c := e.scan("t", 1)
	isNull := e.f.ConstructIs(e.v(c[0]), memo.NullSingleton)
	before := e.f.ConstructSelect(s, e.filters(isNull))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)

	isNotNull := e.f.ConstructIsNot(e.v(c[0]), memo.NullSingleton)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(isNotNull)))

	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name("t"))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	nullable := e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(tabID.ColumnID(1))})
	nullableIsNull := e.f.ConstructIs(e.v(tabID.ColumnID(1)), memo.NullSingleton)
	e.expectNoMatch(rule, e.f.ConstructSelect(nullable, e.filters(nullableIsNull)))
}
